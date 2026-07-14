// AI Proxy Server — 为 CafeBBS Android 客户端提供 AI 聊天代理
// 零 npm 依赖，纯 Node.js 内置模块
// Node.js 20+
//
// 架构:
//   Android App ──Token──► /api/ai-proxy/chat ──► DeepSeek API
//                          │
//                          ├─ ① 验证 Flarum Token (调用论坛 /api/users/me)
//                          ├─ ② 频率限制 (每人每天 N 次)
//                          └─ ③ 转发请求到 DeepSeek → 返回结果
//
// 使用 Docker 部署，与 Flarum 容器在同一 bridge 网络

const http = require("http");
const https = require("https");

// ============================================================
// 配置 (全部从环境变量读取，敏感信息不进代码)
// ============================================================
const CONFIG = {
  // DeepSeek API (必填)
  AI_API_KEY: process.env.OPENAI_API_KEY,
  AI_BASE_URL: process.env.OPENAI_BASE_URL || "https://api.deepseek.com",
  AI_MODEL: process.env.OPENAI_MODEL || "deepseek-v4-flash",

  // Flarum 论坛 (容器内地址)
  FLARUM_URL: process.env.FLARUM_URL || "http://shinsenter_flarum-1:80",

  // 限频
  DAILY_LIMIT: parseInt(process.env.DAILY_LIMIT || "500", 10),

  // 服务端口
  PORT: parseInt(process.env.PORT || "3000", 10),
};

// 启动检查
if (!CONFIG.AI_API_KEY) {
  console.error("❌ 致命错误: 缺少 OPENAI_API_KEY 环境变量");
  console.error("   启动容器时请添加: -e OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx");
  process.exit(1);
}

// ============================================================
// 简易频率限制器 (内存存储，单进程够用)
// ============================================================
const usageMap = new Map(); // key: "userId_2026-07-14" → count

// 每小时清理过期数据，防止内存泄漏
setInterval(() => {
  const today = dateKey();
  let cleaned = 0;
  for (const key of usageMap.keys()) {
    if (!key.endsWith(`_${today}`)) {
      usageMap.delete(key);
      cleaned++;
    }
  }
  if (cleaned > 0) console.log(`🧹 清理了 ${cleaned} 条过期频率记录`);
}, 3600_000);

function dateKey() {
  return new Date().toISOString().slice(0, 10); // "2026-07-14"
}

/** 检查并递增计数，返回 { ok, count, remaining } */
function checkAndIncr(userId) {
  const key = `${userId}_${dateKey()}`;
  const count = (usageMap.get(key) || 0) + 1;
  if (count > CONFIG.DAILY_LIMIT) {
    return { ok: false, count: count - 1 };
  }
  usageMap.set(key, count);
  return { ok: true, count, remaining: CONFIG.DAILY_LIMIT - count };
}

// ============================================================
// HTTP 请求工具 (只用内置模块，零依赖)
// ============================================================
function fetchJson(url, opts = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const mod = u.protocol === "https:" ? https : http;
    const req = mod.request(
      url,
      {
        method: opts.method || "GET",
        headers: { "Content-Type": "application/json", ...opts.headers },
        timeout: 60_000, // 60 秒超时
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf-8");
          let data = null;
          try {
            data = JSON.parse(raw);
          } catch (_) {
            /* 非 JSON 响应 */
          }
          resolve({ status: res.statusCode, data, raw });
        });
      }
    );
    req.on("timeout", () => {
      req.destroy();
      reject(new Error("请求超时"));
    });
    req.on("error", reject);
    if (opts.body) req.write(JSON.stringify(opts.body));
    req.end();
  });
}

// ============================================================
// 验证 Flarum Token
// ============================================================
async function verifyFlarumToken(token) {
  if (!token) return null;
  try {
    const res = await fetchJson(`${CONFIG.FLARUM_URL}/api/users/me`, {
      headers: { Authorization: `Token ${token}` },
    });
    if (res.status !== 200) return null;
    const user = res.data?.data;
    if (!user?.id) return null;
    return {
      id: String(user.id),
      username: user.attributes?.username || "未知用户",
    };
  } catch (e) {
    console.error("验证 Token 失败:", e.message);
    return null;
  }
}

// ============================================================
// System Prompt
// ============================================================
function buildSystemPrompt(username) {
  return `你是"Cafe论坛"的 AI 助手，名叫"小C"。当前对话的用户是 ${username}。

你的职责:
- 用中文交流，态度友好、热情，像朋友一样
- 回答技术问题时要专业、详细
- 如果用户询问论坛使用方法，给出清晰的指引
- 不了解的事情直接说不知道，不要编造信息
- 回复简洁有力，不要啰嗦`;
}

// ============================================================
// 调用 DeepSeek API
// ============================================================
async function callDeepSeek(messages) {
  const body = {
    model: CONFIG.AI_MODEL,
    messages,
    temperature: 0.7,
    max_tokens: 4096,
  };

  const res = await fetchJson(`${CONFIG.AI_BASE_URL}/v1/chat/completions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${CONFIG.AI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body,
  });

  if (res.status !== 200) {
    const errMsg = res.data?.error?.message || res.raw || `HTTP ${res.status}`;
    throw new Error(`DeepSeek API 错误: ${errMsg}`);
  }

  return {
    content: res.data.choices[0].message.content,
    usage: res.data.usage,
  };
}

// ============================================================
// HTTP 服务
// ============================================================
const server = http.createServer(async (req, res) => {
  // CORS 头 (允许 Android 客户端跨域)
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");

  // 预检请求
  if (req.method === "OPTIONS") {
    res.writeHead(204);
    return res.end();
  }

  // 健康检查
  if (req.url === "/health" && req.method === "GET") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ status: "ok", model: CONFIG.AI_MODEL }));
  }

  // 只处理聊天端点
  if (req.url !== "/api/ai-proxy/chat" || req.method !== "POST") {
    res.writeHead(404, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ error: "not_found" }));
  }

  // ── 读取请求体 ──
  const chunks = [];
  req.on("data", (c) => chunks.push(c));
  req.on("end", async () => {
    res.setHeader("Content-Type", "application/json; charset=utf-8");

    try {
      const rawBody = Buffer.concat(chunks).toString("utf-8");
      const body = JSON.parse(rawBody || "{}");

      // ── ① 提取并验证 Token ──
      const authHeader = req.headers.authorization || "";
      const token = authHeader.replace("Token ", "").replace("Bearer ", "");

      const user = await verifyFlarumToken(token);
      if (!user) {
        res.writeHead(401);
        return res.end(
          JSON.stringify({ error: "请先登录论坛账号才能使用 AI 助手" })
        );
      }

      // ── ② 频率限制 ──
      const limit = checkAndIncr(user.id);
      if (!limit.ok) {
        res.writeHead(429);
        return res.end(
          JSON.stringify({
            error: `今日对话次数已用完（${CONFIG.DAILY_LIMIT} 次/天），请明天再来`,
            daily_used: limit.count,
            daily_limit: CONFIG.DAILY_LIMIT,
            daily_remaining: 0,
          })
        );
      }

      // ── ③ 组装消息 ──
      const history = Array.isArray(body.history) ? body.history.slice(-30) : [];
      const messages = [
        { role: "system", content: buildSystemPrompt(user.username) },
        ...history,
        { role: "user", content: body.message || "" },
      ];

      // ── ④ 调用 AI ──
      const aiResult = await callDeepSeek(messages);

      // ── ⑤ 返回结果 ──
      res.writeHead(200);
      res.end(
        JSON.stringify({
          reply: aiResult.content,
          tokens_used: aiResult.usage?.total_tokens || 0,
          daily_used: limit.count,
          daily_remaining: limit.remaining,
          daily_limit: CONFIG.DAILY_LIMIT,
        })
      );

      // 日志
      const tokens = aiResult.usage?.total_tokens || 0;
      console.log(
        `✅ ${user.username}(${user.id}) | ` +
          `第${limit.count}/${CONFIG.DAILY_LIMIT}次 | ` +
          `tokens:${tokens} | ` +
          `剩余:${limit.remaining}`
      );
    } catch (err) {
      console.error("❌", err.message);
      res.writeHead(502);
      res.end(
        JSON.stringify({ error: "AI 服务暂时不可用，请稍后重试" })
      );
    }
  });
});

server.listen(CONFIG.PORT, () => {
  console.log("═".repeat(50));
  console.log("  🚀 CafeBBS AI Proxy 已启动");
  console.log("═".repeat(50));
  console.log(`  监听端口 : ${CONFIG.PORT}`);
  console.log(`  AI 模型  : ${CONFIG.AI_MODEL}`);
  console.log(`  API 地址 : ${CONFIG.AI_BASE_URL}`);
  console.log(`  每日限制 : ${CONFIG.DAILY_LIMIT} 次/人`);
  console.log(`  论坛地址 : ${CONFIG.FLARUM_URL}`);
  console.log("═".repeat(50));
});
