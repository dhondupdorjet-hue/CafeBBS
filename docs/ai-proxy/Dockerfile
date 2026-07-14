FROM node:20-alpine

WORKDIR /app

# 复制 server.js（唯一文件，零 npm 依赖）
COPY server.js .

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

EXPOSE 3000

CMD ["node", "server.js"]
