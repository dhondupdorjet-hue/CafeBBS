# AI Proxy 部署指南

## 架构

```
用户 → Cloudflare Tunnel → bbs.liht.cc/api/ai-proxy/chat → ai-proxy:3000 → DeepSeek API
                                                                  │
                                                         验证 Token  ↑
                                                         shinsenter_flarum-1:80
```

## 第一步：部署 ai-proxy 容器

在 NAS 上执行（SSH 或网页终端）：

```bash
# 先创建配置目录
mkdir -p /volume1/docker/ai-proxy

# 把 server.js 上传到这个目录
# (可以用绿联 NAS 的文件管理器，或者 scp)

# 构建并启动容器
sudo docker run -d \
  --name ai-proxy \
  --network bridge \
  --restart unless-stopped \
  -p 127.0.0.1:3000:3000 \
  -e OPENAI_API_KEY="sk-你的DeepSeek密钥" \
  -e OPENAI_BASE_URL="https://api.deepseek.com" \
  -e OPENAI_MODEL="deepseek-v4-flash" \
  -e FLARUM_URL="http://shinsenter_flarum-1:80" \
  -e DAILY_LIMIT="500" \
  -v /volume1/docker/ai-proxy/server.js:/app/server.js:ro \
  node:20-alpine \
  node /app/server.js
```

> **注意**: 把 `sk-你的DeepSeek密钥` 替换为真实的 DeepSeek API Key。

## 第二步：验证服务

```bash
# 健康检查
curl http://127.0.0.1:3000/health

# 测试聊天 (需要替换为真实 Token)
curl -X POST http://127.0.0.1:3000/api/ai-proxy/chat \
  -H "Authorization: Token 你的论坛Token" \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，介绍一下你自己"}'
```

## 第三步：配置 Cloudflare Tunnel

在 Cloudflare 网页后台添加路由：

1. 打开 Cloudflare Dashboard → **Zero Trust** → **Networks** → **Tunnels**
2. 找到你的 tunnel（`cloudflared-tunnel`）→ 点 **Configure** → **Public Hostname**
3. 新增一条：

| 字段 | 值 |
|---|---|
| Subdomain | `bbs` (或你现有的) |
| Domain | `liht.cc` |
| **Path** | `/api/ai-proxy/*` |
| Type | HTTP |
| URL | `ai-proxy:3000` |

> ⚠️ **注意**: 如果 Cloudflare Tunnel 容器在另一个 Docker 网络（`cloudflared_default`），需要用宿主机 IP 代替 `ai-proxy` 容器名。先在 NAS 上跑：
> ```bash
> sudo docker network connect bridge cloudflared-tunnel
> ```
> 这样 tunnel 就能通过容器名 `ai-proxy` 访问了。

## 回滚

```bash
# 停止并删除容器
sudo docker stop ai-proxy && sudo docker rm ai-proxy

# 在 Cloudflare 后台删除 /api/ai-proxy/* 路由规则

# 完全撤销，不留痕迹
```

## 修改限制

```bash
# 改为每天 1000 次
sudo docker stop ai-proxy && sudo docker rm ai-proxy
sudo docker run -d \
  --name ai-proxy \
  --network bridge \
  --restart unless-stopped \
  -p 127.0.0.1:3000:3000 \
  -e OPENAI_API_KEY="sk-你的密钥" \
  -e DAILY_LIMIT="1000" \
  ... (其余参数同上)
```

## 查看日志

```bash
sudo docker logs -f ai-proxy
```
