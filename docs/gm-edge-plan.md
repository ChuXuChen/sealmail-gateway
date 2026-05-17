# Kona 国密 Edge

Kona Edge 是国密专用 SMTP gateway。标准 SMTP/TLS 仍由 Postfix 直接处理，Edge 只负责约定端口和约定伙伴域的 TLCP / 国密 TLS 1.3。

## 链路

```text
标准入站:
Internet -> Postfix :25/:465/:587 -> SealMail :10025 -> Postfix :10026/:10027

国密入站:
Partner -> Kona Edge :2525/:2465 -> Postfix internal smtpd :2530
  -> SealMail :10025 -> Postfix :10026/:10027

标准出站:
Postfix smtp(8) -> Remote MX

国密出站:
Postfix transport_maps -> Kona Edge :2526 -> Partner GM gateway
```

## 实现边界

- Edge 自研最小 SMTP 状态机，不使用 SubEthaSMTP。
- Edge 只实现 `EHLO/HELO`、`STARTTLS`、`MAIL FROM`、`RCPT TO`、`DATA`、`RSET`、`NOOP`、`QUIT`。
- `2525` 是 SMTP + STARTTLS 国密端口，`2465` 是隐式国密 TLS 端口。
- 国密端口只允许 TLCP / 国密 TLS 1.3；标准 TLS fallback 不作为产品能力提供。
- Edge 不落盘排队、不生成退信、不本地投递、不改写正文。
- Edge 有连接数、收件人数、邮件大小和 SMTP 行长度上限；超过上限会返回 SMTP 临时或永久错误。
- DATA 阶段采用流式转发；下游未返回 `250` 前，Edge 不向上游返回成功。
- Edge 在转发到 Postfix 时追加受控 trace header：`X-Original-Client-IP` 与 `X-SealMail-Edge-Protocol`，SealMail 只能在可信内网链路上使用这些 header。
- 管理端口默认只绑定 `127.0.0.1:2727`，提供 `/health`、`/status`、`/metrics`。

## 配置

示例配置在 `sealmail-backend/sealmail-edge/src/main/resources/sealmail-edge.example.properties`。

真实证书、私钥、keystore、truststore 和密码必须通过运行时 secret 或文件挂载提供，不能提交到仓库。

主应用的标准配置通过 `/api/v1/runtime-policies/gm-edge` 保存到数据库；独立 Edge 进程启动时仍读取 properties 文件。运维侧应由部署系统从该策略生成或同步 `sealmail-edge.properties`，并滚动重启 Edge。

国密出站路由支持两种目标：

```properties
edge.outbound.routes=.partner.example.cn=gm-relay.partner.example.cn:2525
edge.outbound.routes=partner.example.cn=IMPLICIT_TLS://gm-relay.partner.example.cn:2465
```

未写 scheme 时默认为 `STARTTLS`。

Postfix 分流示例：

```bash
GM_DOMAINS=partner.example.cn sudo -E ops/postfix/configure-gm-edge.sh
```

这会新增：

- `smtp-gm` transport：Postfix 到 Edge 的内网 SMTP transport。
- `127.0.0.1:2530` internal smtpd：Edge 国密入站转入 Postfix。
- `/etc/postfix/gm_transport`：国密伙伴域分流表。

## 验收

- 标准 `25/465/587` 入站和普通出站不经过 Edge。
- 国密 `2525` STARTTLS 能完成 TLCP / 国密 TLS 1.3 握手并转入 Postfix。
- 国密 `2465` 隐式 TLS 能完成握手并转入 Postfix。
- 国密伙伴域出站命中 `transport_maps` 后经 Edge 投递。
- 远端 `4xx` 由 Edge 返回给 Postfix，Postfix 重试。
- 远端 `5xx` 由 Edge 返回给 Postfix，Postfix 退信。
