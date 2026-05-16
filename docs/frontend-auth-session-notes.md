# 前端认证会话说明

## 当前实现

- 前端仍使用 `localStorage` 保存 `accessToken` 和当前用户摘要。
- API 客户端在请求拦截器中读取 `accessToken` 并设置 `Authorization: Bearer ...`。
- 非登录接口返回 401 时，API 客户端只清理本地认证材料并派发会话失效事件。
- `AuthProvider` 统一接收会话失效事件，清理认证状态并提示用户重新登录。
- 受保护路由根据认证状态自然回到登录页，不再由 API 层直接刷新页面。

## 安全边界

`localStorage` 中的 Bearer Token 会暴露给同源脚本读取。本实现依赖前端避免 XSS、后端限制 token 有效期，以及 HTTPS 部署来降低风险，但它不能提供 HttpOnly Cookie 的脚本隔离能力。

本阶段不迁移到 HttpOnly Cookie，原因是后端当前仍提供 Bearer Token 登录响应，尚未提供完整的 Cookie 登录、刷新、登出和 CSRF 防护协议。

## 后续迁移条件

迁移到 HttpOnly Cookie 前，后端需要先提供：

- 登录成功后设置 `HttpOnly; Secure; SameSite` Cookie。
- 登出接口可靠清理 Cookie。
- 会话续期或刷新策略。
- 对跨站请求的 CSRF 防护策略。
- 前端 API 客户端切换到 `withCredentials` 并移除 token 持久化。
