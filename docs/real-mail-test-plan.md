# 实际邮件功能覆盖测试计划

固定测试账号：

```text
发件方: cxc1234567892022@163.com
收件方: 2416507029@qq.com
```

本文分两层：

- 第一层 `T01` 到 `T07` 是最小主流程集，用尽可能少的实际邮件覆盖 SMTP 接入、方向识别、路由、Relay、出站 DLP 四种动作、S/MIME 签名/加密、DKIM 出站签名、入站认证失败、入站 S/MIME 解密/验签、隔离放行和异常邮件。
- 第二层 `E01` 到 `E20` 是完整扩展覆盖集，用来补齐主流程集没有覆盖的 HTML/附件 DLP、DLP 策略模式和作用域、隔离队列边界操作、证书信任/吊销/过期、国密算法、GM Edge、真实 SPF/DKIM/DMARC PASS、多收件人和 Relay 故障。

重要限制：固定使用 `@163.com` 和 `@qq.com` 可以覆盖网关内部功能，但不能完整证明公网 DKIM/SPF/DMARC 合规。因为你没有 `163.com` 和 `qq.com` 的 DNS 控制权，无法发布 `selector._domainkey.163.com`、`_dmarc.163.com` 或 SPF 记录。本文中的 DKIM/SPF/DMARC 测试以“网关是否执行、是否写入 `Authentication-Results`、失败策略是否生效”为准；如果要测试认证全部 PASS，必须换成你能控制 DNS 的自有域名。多收件人、GM Edge 互通和国密 TLS 也需要额外测试域名或伙伴网关，不能只靠固定的一发一收两只公网邮箱完成。

本文的“覆盖完全”限定为邮件处理链路覆盖。登录、用户权限、证书签发/导入表单、策略 CRUD、前端页面权限等纯管理功能，不能靠几封邮件本身验证，需要另配 API/UI 测试。

## 0. 通用准备

### 0.1 运行方式

后端、Postfix content filter、前端按 `docs/configuration-guide.md` 启动。标准链路：

```text
邮件客户端/脚本 -> Postfix:25 -> SealMail:10025 -> Postfix:10026/10027 -> 后续投递
```

如果希望最终真的投到 QQ 收件箱，建议让 Postfix 做外发 smart host，并使用 `cxc1234567892022@163.com` 的 SMTP 授权码做认证。否则从自有服务器直接伪装 `@163.com` 投递，QQ 很可能因 SPF/DMARC/信誉策略拒收或进垃圾箱。Postfix 的 163 smarthost 配置不属于本项目代码，按当前 163 邮箱后台给出的 SMTP 主机、端口、安全方式和授权码配置。

不要直接从 163 Web 邮箱给 QQ 发这些测试邮件。那条链路是 `163 -> QQ`，不会经过本机 Postfix/SealMail。实际测试时应使用 `swaks`、邮件客户端或脚本把邮件提交到你的 Postfix/SealMail 入口，再由 Postfix/Relay 送往 QQ。

### 0.2 获取 API Token

以下配置示例都假定已有管理员 token：

```bash
TOKEN='replace-with-login-token'
API='http://localhost:8080'
```

### 0.3 两个域名模式

因为后端方向识别规则是“发件人域名是本地域名则出站，否则收件人域名是本地域名则入站”，同一组发/收件地址需要切换域名配置。

出站模式，用于 T01 到 T05：

```json
{
  "domain": "163.com",
  "localDomain": true,
  "encryptionPolicy": "NO_ENCRYPTION",
  "preferredAlgorithm": "AUTO",
  "signingEnabled": false,
  "dkimEnabled": false,
  "active": true
}
```

入站模式，用于 T06 到 T07：

```json
[
  {
    "domain": "163.com",
    "localDomain": false,
    "encryptionPolicy": "ALLOW",
    "preferredAlgorithm": "AUTO",
    "signingEnabled": false,
    "dkimEnabled": false,
    "active": true
  },
  {
    "domain": "qq.com",
    "localDomain": true,
    "encryptionPolicy": "NO_ENCRYPTION",
    "preferredAlgorithm": "AUTO",
    "signingEnabled": false,
    "dkimEnabled": false,
    "active": true
  }
]
```

`localDomain` 只能在创建域名配置时指定，当前 `PUT /api/v1/domains/{id}` 不能修改它。因此出站/入站模式切换建议用前端删除并重建对应域名配置，或在测试数据库中临时更新 `domain_config.is_local`。不要在生产库直接改表。

用 API 删除重建的流程：

```text
GET  /api/v1/domains/domain/{domain}
DELETE /api/v1/domains/{id}
POST /api/v1/domains
PUT  /api/v1/domains/{id}
```

删除重建会生成新的域名配置 ID。证书、DLP、邮件认证策略主要按邮箱/域名匹配，一般不依赖这个 ID。

### 0.4 证书准备

为了覆盖 S/MIME，需要至少两张终端证书：

| 邮箱 | 用途 | 要求 |
| --- | --- | --- |
| `cxc1234567892022@163.com` | 出站 S/MIME 签名、入站验签 | RSA 终端证书，受信任，有私钥，绑定 `SIGNING`。 |
| `2416507029@qq.com` | 出站 S/MIME 加密、入站解密 | RSA 终端证书，受信任，有私钥，绑定 `ENCRYPTION`。 |

可以在前端证书页面签发，也可以调用：

```text
POST /api/v1/certificates/issue
POST /api/v1/certificate-bindings
```

示例请求，`intermediateCaId` 换成 RSA Intermediate CA 的证书 ID：

```bash
curl -X POST "$API/api/v1/certificates/issue" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "intermediateCaId": "RSA_INTERMEDIATE_CA_ID",
    "ownerEmail": "cxc1234567892022@163.com",
    "algorithm": "RSA",
    "alias": "cxc-163-signing",
    "trusted": true
  }'

curl -X POST "$API/api/v1/certificates/issue" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "intermediateCaId": "RSA_INTERMEDIATE_CA_ID",
    "ownerEmail": "2416507029@qq.com",
    "algorithm": "RSA",
    "alias": "qq-recipient-encryption",
    "trusted": true
  }'
```

绑定证书用途：

```bash
curl -X POST "$API/api/v1/certificate-bindings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ownerEmail": "cxc1234567892022@163.com",
    "certificateId": "CXC_163_CERT_ID",
    "purpose": "SIGNING",
    "enabled": true
  }'

curl -X POST "$API/api/v1/certificate-bindings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ownerEmail": "2416507029@qq.com",
    "certificateId": "QQ_CERT_ID",
    "purpose": "ENCRYPTION",
    "enabled": true
  }'
```

### 0.5 DKIM 准备

生成一把测试 RSA DKIM 私钥，放在仓库外：

```bash
openssl genrsa -out /run/secrets/dkim_163_test_private.pem 2048
```

把 `163.com` 的域名级邮件认证策略配置为启用 DKIM 签名：

```bash
curl -X PUT "$API/api/v1/mail-auth/domains/163.com/policy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "dkimSigningEnabled": true,
    "dkimSelector": "sealmail-test",
    "dkimKeySecretRef": "file:/run/secrets/dkim_163_test_private.pem",
    "dkimSignedHeaders": ["from", "to", "subject", "date", "message-id"]
  }'
```

这会让网关给出站邮件添加 `DKIM-Signature: d=163.com; s=sealmail-test; ...`。公网收件方无法通过它验证，除非你能发布 `sealmail-test._domainkey.163.com`。

### 0.6 DLP 规则准备

建立 4 条规则，分别用固定 token 触发。建议所有规则 `contentKinds` 至少包含 `SUBJECT`、`BODY_TEXT`。

| 规则名 | 正则 | 动作 | 用途 |
| --- | --- | --- | --- |
| `TEST_WARN` | `SM-WARN-2026` | `WARN` | 命中但放行。 |
| `TEST_MUST_ENCRYPT` | `SM-MUST-ENCRYPT-2026` | `MUST_ENCRYPT` | 命中后强制加密。 |
| `TEST_QUARANTINE` | `SM-QUARANTINE-2026` | `QUARANTINE` | 进入 DLP 隔离队列。 |
| `TEST_BLOCK` | `SM-BLOCK-2026` | `BLOCK` | 阻断并进入异常邮件。 |

示例：

```bash
curl -X POST "$API/api/v1/dlp/patterns" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "TEST_WARN",
    "description": "实际邮件测试 WARN",
    "regex": "SM-WARN-2026",
    "type": "REGEX",
    "contentKinds": ["SUBJECT", "BODY_TEXT"],
    "minMatchCount": 1,
    "maxEvidenceCount": 5,
    "maskingStrategy": "DEFAULT",
    "action": "WARN",
    "severity": 3,
    "priority": 10,
    "enabled": true
  }'
```

其余 3 条只替换 `name`、`description`、`regex`、`action`、`severity`、`priority`。建议严重度/优先级：

```text
TEST_MUST_ENCRYPT: severity=6, priority=20
TEST_QUARANTINE:   severity=8, priority=30
TEST_BLOCK:        severity=10, priority=40
```

如果系统里启用了 DLP selections，新增全局选择，确保这些规则能命中：

```bash
curl -X POST "$API/api/v1/dlp/selections" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scopeType": "GLOBAL",
    "patternMode": "ALL",
    "enabled": true
  }'
```

## 1. 第一层：最小主流程测试邮件清单

| 编号 | 邮件主题 | 覆盖能力 | 是否应投递到 QQ |
| --- | --- | --- | --- |
| T01 | `[SealMail-T01] WARN 放行与基础投递` | SMTP 接入、出站路由、DLP WARN、Relay、审计。 | 是，若 Postfix 外发可达。 |
| T02 | `[SealMail-T02] MUST_ENCRYPT + S/MIME + DKIM` | DLP MUST_ENCRYPT、S/MIME 签名、S/MIME 加密、DKIM 签名、加密 Relay 成功。 | 是，但 QQ 可能只能收到 S/MIME 加密附件。 |
| T03 | `[SealMail-T03] DLP QUARANTINE 隔离` | DLP 隔离队列、隔离详情、证据、放行流程。 | 初始不投递；放行后投递。 |
| T04 | `[SealMail-T04] DLP BLOCK 阻断` | DLP 阻断、异常邮件、失败审计。 | 否。 |
| T05 | `[SealMail-T05] 强制加密缺证书失败` | 出站证书缺失、加密失败、异常邮件。 | 否。 |
| T06 | `[SealMail-T06] 入站认证失败隔离` | 入站方向、邮件认证、`Authentication-Results`、认证失败策略。 | 否，进入异常邮件。 |
| T07 | `[SealMail-T07] 入站 S/MIME 解密与验签` | 入站方向、S/MIME 解密、S/MIME 验签、入站 Relay。 | 是，若 10026 后续投递可达。 |

T02 的加密后原始 MIME 可以复用于 T07，因此实际人工编写的正文只有 6 组；T07 是对 T02 产物的回灌测试。

## 2. T01 - WARN 放行与基础投递

### 配置

切到出站模式：

```text
163.com localDomain=true active=true
```

域名策略：

```json
{
  "encryptionPolicy": "NO_ENCRYPTION",
  "preferredAlgorithm": "AUTO",
  "signingEnabled": false,
  "dkimEnabled": false,
  "active": true
}
```

DLP：启用 `TEST_WARN`，其他 3 条可启用但正文不要包含它们的 token。

### 邮件

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T01] WARN 放行与基础投递

这是一封 SealMail 实际邮件测试 T01。

触发 DLP WARN 的测试标记:
SM-WARN-2026

预期:
1. 网关识别为 OUTBOUND。
2. DLP 命中 TEST_WARN，但最终动作为 WARN。
3. 邮件继续 Relay。
4. 审计中出现 EMAIL_ROUTED、DLP_VIOLATION、EMAIL_RELAYED。
```

### 验证

- QQ 收件箱或垃圾箱收到邮件。
- DLP 事件里有 `TEST_WARN`。
- DLP 隔离队列没有新增该邮件。
- 异常邮件没有新增该邮件。

## 3. T02 - MUST_ENCRYPT + S/MIME + DKIM

### 配置

切到出站模式。

域名策略：

```json
{
  "encryptionPolicy": "ALLOW",
  "preferredAlgorithm": "STANDARD_ONLY",
  "signingEnabled": true,
  "dkimEnabled": true,
  "active": true
}
```

证书：

- `cxc1234567892022@163.com` 有受信任 `SIGNING` 证书和私钥。
- `2416507029@qq.com` 有受信任 `ENCRYPTION` 证书。

邮件认证：

- `163.com` 域名级 DKIM 策略启用。
- `dkimKeySecretRef=file:/run/secrets/dkim_163_test_private.pem`。

DLP：启用 `TEST_MUST_ENCRYPT`。

### 邮件

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T02] MUST_ENCRYPT + S/MIME + DKIM

这是一封 SealMail 实际邮件测试 T02。

触发 DLP MUST_ENCRYPT 的测试标记:
SM-MUST-ENCRYPT-2026

预期:
1. 网关识别为 OUTBOUND。
2. DLP 命中 TEST_MUST_ENCRYPT，并设置 mustEncrypt。
3. 网关使用 cxc1234567892022@163.com 的证书做 S/MIME 签名。
4. 网关使用 2416507029@qq.com 的证书做 S/MIME 加密。
5. 网关添加 DKIM-Signature。
6. 邮件 Relay 成功。
```

### 验证

- 审计中出现 `EMAIL_SIGNED`、`EMAIL_ENCRYPTED`、`EMAIL_RELAYED`。
- 投递出的 MIME 中应出现 S/MIME 加密结构，例如 `application/pkcs7-mime` 或 `smime.p7m`。
- 投递出的 MIME 头部应出现 `DKIM-Signature`。
- QQ Web 邮箱可能无法直接阅读加密正文，这是预期现象；完整解密需要收件端持有对应私钥。

保留这封邮件投递后的原始 MIME，T07 会复用它测试入站解密与验签。

## 4. T03 - DLP QUARANTINE 隔离

### 配置

切到出站模式。

域名策略建议关闭 S/MIME 和 DKIM，减少干扰：

```json
{
  "encryptionPolicy": "NO_ENCRYPTION",
  "preferredAlgorithm": "AUTO",
  "signingEnabled": false,
  "dkimEnabled": false,
  "active": true
}
```

DLP：启用 `TEST_QUARANTINE`。

隔离策略建议先设为：

```json
{
  "maxRetentionDays": 30,
  "notificationEnabled": false,
  "releaseRequiresEncryption": false
}
```

### 邮件

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T03] DLP QUARANTINE 隔离

这是一封 SealMail 实际邮件测试 T03。

触发 DLP QUARANTINE 的测试标记:
SM-QUARANTINE-2026

预期:
1. 网关识别为 OUTBOUND。
2. DLP 命中 TEST_QUARANTINE。
3. 邮件进入 DLP 隔离队列。
4. 初始不会投递到 QQ。
```

### 验证

- 前端 DLP 隔离页面出现该邮件。
- 隔离详情能看到命中规则和脱敏证据。
- 审计中出现 `DLP_QUARANTINE` 或对应 `DLP_VIOLATION`。

### 放行测试

对 T03 执行一次普通放行：

```text
POST /api/v1/dlp/quarantine/{id}/release
```

然后确认：

- 邮件被重新进入释放流程。
- `releaseRequiresEncryption=false` 时可直接 Relay。
- QQ 收件箱或垃圾箱收到放行后的邮件。

如果要覆盖“放行必须加密”，把隔离策略改为：

```json
{
  "releaseRequiresEncryption": true
}
```

重新发送 T03，再放行。此时需要 `2416507029@qq.com` 有可用加密证书，否则放行会失败。

## 5. T04 - DLP BLOCK 阻断

### 配置

切到出站模式。

域名策略建议：

```json
{
  "encryptionPolicy": "NO_ENCRYPTION",
  "preferredAlgorithm": "AUTO",
  "signingEnabled": false,
  "dkimEnabled": false,
  "active": true
}
```

DLP：启用 `TEST_BLOCK`。

### 邮件

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T04] DLP BLOCK 阻断

这是一封 SealMail 实际邮件测试 T04。

触发 DLP BLOCK 的测试标记:
SM-BLOCK-2026

预期:
1. 网关识别为 OUTBOUND。
2. DLP 命中 TEST_BLOCK。
3. 邮件进入异常邮件，不进入 DLP 隔离队列。
4. 不投递到 QQ。
```

### 验证

- 异常邮件页面出现该邮件。
- DLP 隔离页面不应出现该邮件。
- 审计中有 DLP `BLOCK` 相关记录。

## 6. T05 - 强制加密缺证书失败

### 配置

切到出站模式。

域名策略：

```json
{
  "encryptionPolicy": "MANDATORY",
  "preferredAlgorithm": "STANDARD_ONLY",
  "signingEnabled": false,
  "dkimEnabled": false,
  "active": true
}
```

临时让 `2416507029@qq.com` 没有可用加密证书，任选一种方式：

- 删除或禁用 `2416507029@qq.com` 的 `ENCRYPTION` 证书绑定。
- 将该证书取消信任。
- 吊销该证书。

### 邮件

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T05] 强制加密缺证书失败

这是一封 SealMail 实际邮件测试 T05。

正文不包含任何 DLP token。

预期:
1. 网关识别为 OUTBOUND。
2. 域名策略要求 MANDATORY 加密。
3. 因收件人没有可用加密证书，路由或加密阶段失败。
4. 邮件进入异常邮件。
5. 不投递到 QQ。
```

### 验证

- 异常邮件页面出现该邮件。
- 失败详情包含证书缺失、无法加密或 recipient certificate 相关信息。
- 审计中有 `EMAIL_CERTIFICATE_SELECTED`、`EMAIL_ROUTED` 失败或 `EMAIL_RELAYED` 失败前的记录。

完成后恢复 `2416507029@qq.com` 的加密证书和绑定，避免影响 T07。

## 7. T06 - 入站认证失败隔离

### 配置

切到入站模式：

```text
163.com localDomain=false active=true
qq.com  localDomain=true  active=true
```

全局邮件认证策略：

```bash
curl -X PUT "$API/api/v1/mail-auth/policy" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "authservId": "sealmail-gateway",
    "trustedProxyMode": "TRUSTED_HEADERS",
    "failureDefaultAction": "FORCE_QUARANTINE"
  }'
```

部署级 trusted headers 默认信任 loopback，并读取 `X-Original-Client-IP` / `X-Forwarded-For`。测试时从本机注入邮件即可。

### 邮件

这封邮件需要能带自定义头。用 `swaks`、`sendmail` 或本地 SMTP 脚本发送，不建议用普通 Web 邮箱界面。

```text
X-Original-Client-IP: 203.0.113.200
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T06] 入站认证失败隔离

这是一封 SealMail 实际邮件测试 T06。

正文不包含任何 DLP token。

预期:
1. 网关识别为 INBOUND。
2. 使用 X-Original-Client-IP 作为原始公网来源。
3. 对 163.com 执行 SPF/DKIM/DMARC。
4. 由于来源 IP 是测试保留地址，认证应失败或无法通过。
5. failureDefaultAction=FORCE_QUARANTINE，邮件进入异常邮件。
6. 网关写入 Authentication-Results。
```

示例发送：

```bash
swaks \
  --server 127.0.0.1 \
  --port 25 \
  --from cxc1234567892022@163.com \
  --to 2416507029@qq.com \
  --header "X-Original-Client-IP: 203.0.113.200" \
  --header "Subject: [SealMail-T06] 入站认证失败隔离" \
  --body "这是一封 SealMail 实际邮件测试 T06。\n\n正文不包含任何 DLP token。"
```

### 验证

- 异常邮件页面出现该邮件，原因应与 `EMAIL_AUTH_FAILED` 或认证失败相关。
- 原始内容或审计中能看到 `Authentication-Results`。
- 不应投递到 QQ。

完成后把全局邮件认证策略恢复为非阻断，避免影响后续入站正向测试：

```json
{
  "failureDefaultAction": "LOG_ONLY"
}
```

## 8. T07 - 入站 S/MIME 解密与验签

### 配置

切到入站模式。

证书：

- `2416507029@qq.com` 的加密证书必须有私钥，且受信任。
- `cxc1234567892022@163.com` 的签名证书必须受信任。

全局邮件认证策略建议：

```json
{
  "enabled": true,
  "trustedProxyMode": "DISABLED",
  "failureDefaultAction": "LOG_ONLY"
}
```

DLP：本测试正文不要包含任何 DLP token。

### 邮件

复用 T02 投递后的原始 MIME，保持：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-T07] 入站 S/MIME 解密与验签
```

如果无法从 T02 获取原始 MIME，也可以用 S/MIME 客户端手工构造一封：

- 使用 `cxc1234567892022@163.com` 的私钥签名。
- 使用 `2416507029@qq.com` 的公钥加密。
- 通过本机 SMTP 送入 Postfix/SealMail。

正文：

```text
这是一封 SealMail 实际邮件测试 T07。

正文不包含任何 DLP token。

预期:
1. 网关识别为 INBOUND。
2. 检测到 S/MIME 加密并解密。
3. 检测到 S/MIME 签名并验签。
4. DLP 不命中。
5. 邮件 Relay 到 Postfix after-filter 端口。
```

### 验证

- 审计中出现 `EMAIL_DECRYPTED`。
- 如果 MIME 是签名后再加密，解密后应继续出现 `EMAIL_VERIFIED`。
- 邮件最终进入后续投递路径。
- 异常邮件和 DLP 隔离队列不应新增该邮件。

## 9. 第一层覆盖矩阵

| 能力 | 覆盖邮件 |
| --- | --- |
| SMTP 接入 | T01-T07 |
| 出站方向识别 | T01-T05 |
| 入站方向识别 | T06-T07 |
| Relay 成功 | T01、T02、T03 放行、T07 |
| DLP WARN | T01 |
| DLP MUST_ENCRYPT | T02 |
| DLP QUARANTINE | T03 |
| DLP BLOCK | T04 |
| S/MIME 签名 | T02 |
| S/MIME 加密 | T02 |
| S/MIME 解密 | T07 |
| S/MIME 验签 | T07 |
| DKIM 出站签名 | T02 |
| SPF/DKIM/DMARC 入站执行 | T06 |
| 认证失败策略 | T06 |
| 缺证书失败路径 | T05 |
| DLP 隔离放行 | T03 放行 |
| 异常邮件 | T04、T05、T06 |

第一层仍不是全覆盖。它没有覆盖 DLP 的所有内容来源、策略优先级、监控模式、隔离队列所有人工操作、证书吊销/过期/不受信任、国密算法、GM Edge、真实邮件认证 PASS、多收件人和 Relay 故障。下面第二层用于补齐这些缺口。

## 10. 第二层：完整扩展测试邮件清单

第二层尽量复用固定发件方和收件方。凡是不能只靠 `cxc1234567892022@163.com -> 2416507029@qq.com` 完成的项目，都在“额外条件”中明确写出。

| 编号 | 邮件主题 | 补齐能力 | 额外条件 |
| --- | --- | --- | --- |
| E01 | `[SealMail-E01] 入站普通明文放行` | 入站干净邮件、认证记录模式、普通 Relay。 | 无。 |
| E02 | `[SealMail-E02] DLP HTML 正文` | `BODY_HTML` 提取与证据。 | 发送 multipart/alternative 或 text/html。 |
| E03 | `[SealMail-E03] DLP Header 命中` | `HEADERS` 扫描。 | 能添加自定义邮件头。 |
| E04 | `[SealMail-E04] DLP 文本附件` | `ATTACHMENT_TEXT` 提取。 | 带 `.txt` 附件。 |
| E05 | `[SealMail-E05] DLP PDF 附件` | `ATTACHMENT_PDF` 提取。 | 带可搜索文本 PDF。 |
| E06 | `[SealMail-E06] DLP ZIP 附件` | `ATTACHMENT_ZIP_ENTRY` 提取。 | 带 `.zip`，zip 内含文本文件。 |
| E07 | `[SealMail-E07] DLP 附件元数据` | `ATTACHMENT_METADATA` 扫描文件名。 | 带指定文件名附件。 |
| E08 | `[SealMail-E08] DLP MONITOR 降级` | `MONITOR` 模式、推荐动作和实际动作分离。 | 启用 DLP policy/rule-group。 |
| E09 | `[SealMail-E09] DLP 作用域与优先级` | policy 方向、发件域、收件域、优先级、ruleGroup 选择。 | 启用两组 DLP policy。 |
| E10 | `[SealMail-E10] DLP 内置规则` | `BUILTIN` 检测器。 | 使用测试手机号。 |
| E11 | `[SealMail-E11] 隔离拒绝与误报` | `reject`、`false-positive`。 | 复用或重发隔离邮件。 |
| E12 | `[SealMail-E12] 隔离批量操作` | `batch-release`、`batch-reject`。 | 至少两封隔离邮件。 |
| E13 | `[SealMail-E13] 放行强制加密失败` | `releaseRequiresEncryption`、放行失败恢复。 | 禁用收件人加密证书。 |
| E14 | `[SealMail-E14] 多收件人部分缺证书` | 多收件人证书缺失失败。 | 需要第二个收件地址。 |
| E15 | `[SealMail-E15] 国密 S/MIME` | SM2/SM3/SM4 profile。 | 需要 SM2 证书和本地国密能力。 |
| E16 | `[SealMail-E16] 加密 Profile 不匹配` | `GM_ONLY` / `STANDARD_ONLY` 与证书族不匹配。 | 准备 RSA/SM2 不匹配组合。 |
| E17 | `[SealMail-E17] 证书信任、吊销、过期` | untrusted/revoked/expired 证书失败路径。 | 需要专门证书或临时吊销。 |
| E18 | `[SealMail-E18] Relay 故障` | Relay 超时/连接失败、异常邮件。 | 临时改 Relay 到不可达端口。 |
| E19 | `[SealMail-E19] 真实邮件认证 PASS` | SPF/DKIM/DMARC 全 PASS。 | 必须使用自有域名和 DNS。 |
| E20 | `[SealMail-E20] GM Edge 入站/出站` | TLCP/国密 TLS Edge、Postfix 分流。 | 需要 GM Edge、Kona/TLCP 客户端或伙伴网关。 |

### E01 - 入站普通明文放行

配置：

- 切到入站模式：`163.com localDomain=false`，`qq.com localDomain=true`。
- 全局邮件认证策略：`enabled=true`，`trustedProxyMode=DISABLED`，`failureDefaultAction=LOG_ONLY`。
- DLP 测试规则可启用，但正文不要包含任何 DLP token。
- `qq.com` 域名策略保持 `NO_ENCRYPTION`，不强制 S/MIME。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E01] 入站普通明文放行

这是一封 SealMail 扩展测试 E01。

正文不包含 DLP token，也不做 S/MIME 加密或签名。
```

预期：

- 网关识别为 `INBOUND`。
- 认证结果以记录模式写入 `Authentication-Results` 或处理轨迹。
- 不进入 DLP 隔离队列，不进入异常邮件。
- 邮件 Relay 到 after-filter 端口。

### E02 - DLP HTML 正文

新增或修改一条 DLP 规则：

```json
{
  "name": "TEST_HTML_BODY",
  "type": "REGEX",
  "regex": "SM-HTML-2026",
  "contentKinds": ["BODY_HTML"],
  "action": "QUARANTINE",
  "severity": 7,
  "priority": 51,
  "enabled": true
}
```

邮件必须是 HTML 或 multipart/alternative：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E02] DLP HTML 正文
Content-Type: text/html; charset=UTF-8

<html><body><p>HTML DLP token: <strong>SM-HTML-2026</strong></p></body></html>
```

预期：

- DLP 事件 `action=QUARANTINE`。
- 证据的 `partKind` 为 `BODY_HTML`。
- 邮件进入 DLP 隔离队列，初始不投递。

### E03 - DLP Header 命中

新增规则：

```json
{
  "name": "TEST_HEADER",
  "type": "REGEX",
  "regex": "SM-HEADER-2026",
  "contentKinds": ["HEADERS"],
  "action": "WARN",
  "severity": 3,
  "priority": 52,
  "enabled": true
}
```

邮件：

```text
X-SealMail-Dlp-Test: SM-HEADER-2026
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E03] DLP Header 命中

这是一封 SealMail 扩展测试 E03。
```

预期：DLP 事件命中 `TEST_HEADER`，证据 `partKind=HEADERS`，最终动作 `WARN`，邮件继续 Relay。

### E04 - DLP 文本附件

新增规则：

```json
{
  "name": "TEST_ATTACHMENT_TEXT",
  "type": "REGEX",
  "regex": "SM-ATTACH-TEXT-2026",
  "contentKinds": ["ATTACHMENT_TEXT"],
  "action": "QUARANTINE",
  "severity": 8,
  "priority": 53,
  "enabled": true
}
```

邮件主题为 `[SealMail-E04] DLP 文本附件`，正文普通，附件 `e04.txt` 内容：

```text
SealMail E04 text attachment token:
SM-ATTACH-TEXT-2026
```

预期：DLP 证据 `partKind=ATTACHMENT_TEXT`，文件名为 `e04.txt`，邮件进入 DLP 隔离队列。

### E05 - DLP PDF 附件

新增规则：

```json
{
  "name": "TEST_ATTACHMENT_PDF",
  "type": "REGEX",
  "regex": "SM-PDF-2026",
  "contentKinds": ["ATTACHMENT_PDF"],
  "action": "QUARANTINE",
  "severity": 8,
  "priority": 54,
  "enabled": true
}
```

邮件主题为 `[SealMail-E05] DLP PDF 附件`，附件 `e05.pdf` 必须是可搜索文本 PDF，正文或 PDF 文本中包含：

```text
SM-PDF-2026
```

预期：DLP 证据 `partKind=ATTACHMENT_PDF`，邮件进入 DLP 隔离队列。如果 PDF 是扫描图片或文本抽取失败，测试结果应记录为“PDF 文本提取能力缺口”，不要改用正文 token 规避。

### E06 - DLP ZIP 附件

新增规则：

```json
{
  "name": "TEST_ATTACHMENT_ZIP",
  "type": "REGEX",
  "regex": "SM-ZIP-2026",
  "contentKinds": ["ATTACHMENT_ZIP_ENTRY"],
  "action": "QUARANTINE",
  "severity": 8,
  "priority": 55,
  "enabled": true
}
```

邮件主题为 `[SealMail-E06] DLP ZIP 附件`，附件 `e06.zip` 内含 `secret.txt`：

```text
SM-ZIP-2026
```

预期：DLP 证据 `partKind=ATTACHMENT_ZIP_ENTRY`，证据中能定位 zip entry，邮件进入 DLP 隔离队列。

### E07 - DLP 附件元数据

新增规则：

```json
{
  "name": "TEST_ATTACHMENT_METADATA",
  "type": "REGEX",
  "regex": "SM-META-2026",
  "contentKinds": ["ATTACHMENT_METADATA"],
  "action": "WARN",
  "severity": 3,
  "priority": 56,
  "enabled": true
}
```

邮件主题为 `[SealMail-E07] DLP 附件元数据`，附件文件名使用 `SM-META-2026-report.txt`，附件内容可以为空或普通文本。

预期：DLP 命中文件名，证据 `partKind=ATTACHMENT_METADATA`，最终动作 `WARN`，邮件继续 Relay。

### E08 - DLP MONITOR 降级

建立规则、规则组和策略：

- 规则 `TEST_MONITOR_BLOCK`：`pattern=SM-MONITOR-BLOCK-2026`，`type=REGEX`，`contentKinds=["BODY_TEXT"]`，`action=BLOCK`。
- 规则组 `TEST_MONITOR_GROUP` 包含该规则。
- 策略 `TEST_MONITOR_POLICY`：`mode=MONITOR`，`direction=OUTBOUND`，`senderDomains=["163.com"]`，绑定该规则组。启用 policy 后会走 policy/rule-group 路径，不再回退到 legacy selection。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E08] DLP MONITOR 降级

这是一封 SealMail 扩展测试 E08。

SM-MONITOR-BLOCK-2026
```

预期：

- 若用 `POST /api/v1/dlp/policies/{id}/simulate` 预检，应看到 `recommendedAction=BLOCK`、`action=WARN`、`monitorMode=true`。
- 真实邮件产生的 DLP event 应看到 `action=WARN`、`monitorMode=true`；持久化 event 不单独暴露 `recommendedAction`。
- 邮件不被阻断，不进隔离，继续 Relay。

### E09 - DLP 作用域与优先级

建立两套 policy：

- `TEST_SCOPE_LOW`：`mode=ENFORCE`，`direction=OUTBOUND`，`recipientDomains=["qq.com"]`，规则 `pattern=SM-SCOPE-2026 -> WARN`，`priority=200`。
- `TEST_SCOPE_HIGH`：`mode=ENFORCE`，`direction=OUTBOUND`，`senderDomains=["163.com"]`，规则 `pattern=SM-SCOPE-2026 -> QUARANTINE`，`priority=10`。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E09] DLP 作用域与优先级

SM-SCOPE-2026
```

预期：

- 两个 policy 都匹配时，DLP 事件的 `policyIds` 和 `ruleGroupIds` 应能反映参与评估的策略/规则组，顺序应体现 policy priority。
- 由于命中动作中 `QUARANTINE` 优先级高于 `WARN`，最终动作应为 `QUARANTINE`。
- 如果只启用其中一个 policy，则结果应随对应 policy 变化，用于验证作用域匹配。

### E10 - DLP 内置规则

新增内置规则：

```json
{
  "name": "TEST_BUILTIN_PHONE",
  "type": "BUILTIN",
  "builtinCode": "PHONE_CN",
  "contentKinds": ["BODY_TEXT"],
  "action": "WARN",
  "severity": 4,
  "priority": 57,
  "enabled": true
}
```

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E10] DLP 内置规则

这是一封 SealMail 扩展测试 E10。

测试手机号: 13800138000
```

预期：DLP 命中 `TEST_BUILTIN_PHONE`，最终动作 `WARN`，邮件继续 Relay。该手机号只作为测试模式样本，不要使用真实个人号码。

### E11 - 隔离拒绝与误报

重发一封 T03 或任意 `QUARANTINE` 邮件，主题改为 `[SealMail-E11] 隔离拒绝与误报`。确认进入 DLP 隔离队列后执行：

```text
POST /api/v1/dlp/quarantine/{id}/false-positive
POST /api/v1/dlp/quarantine/{id}/reject
```

请求体示例：

```json
{
  "comment": "SealMail E11 false-positive review"
}
```

```json
{
  "rejectedBy": "admin",
  "comment": "SealMail E11 reject"
}
```

预期：

- 隔离邮件 `falsePositive=true`，误报备注可查询。
- 拒绝后状态为 `REJECTED`。
- 再次普通 release 应失败；如需验证强制放行，可调用 release 时传 `force=true`。

### E12 - 隔离批量操作

准备至少两封隔离邮件，建议主题：

```text
[SealMail-E12-A] 隔离批量放行
[SealMail-E12-B] 隔离批量拒绝
```

第一组执行：

```text
POST /api/v1/dlp/quarantine/batch-release
```

第二组执行：

```text
POST /api/v1/dlp/quarantine/batch-reject
```

请求体均为隔离 ID 数组：

```json
["QUARANTINE_ID_1", "QUARANTINE_ID_2"]
```

预期：批量放行的邮件状态变为 `RELEASED` 并进入 Relay；批量拒绝的邮件状态变为 `REJECTED`，不投递。

### E13 - 放行强制加密失败

配置：

- 隔离策略 `releaseRequiresEncryption=true`。
- 临时禁用或吊销 `2416507029@qq.com` 的 `ENCRYPTION` 证书绑定。
- 准备一封 `QUARANTINE` 邮件，主题 `[SealMail-E13] 放行强制加密失败`。

放行：

```text
POST /api/v1/dlp/quarantine/{id}/release
```

预期：

- 放行流程尝试加密后释放。
- 因缺少收件人加密证书，释放失败。
- 隔离项应恢复为可重试状态，或在异常情况下停留 `RELEASING` 后可用 `POST /api/v1/dlp/quarantine/{id}/release/restore` 恢复。
- 异常详情包含 `QUARANTINE_RELEASE_ENCRYPTION_FAILED` 或证书缺失相关信息。

完成后恢复 `releaseRequiresEncryption=false`，并恢复收件人证书。

### E14 - 多收件人部分缺证书

此项不能只靠固定收件方完成。需要新增第二个收件地址，例如：

```text
2416507029@qq.com
missing-cert@example.test
```

配置：

- 出站模式。
- `163.com` 域名策略 `encryptionPolicy=MANDATORY`，`preferredAlgorithm=STANDARD_ONLY`。
- `2416507029@qq.com` 有可用 RSA 加密证书。
- `missing-cert@example.test` 没有加密证书。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com, missing-cert@example.test
Subject: [SealMail-E14] 多收件人部分缺证书

这是一封 SealMail 扩展测试 E14。
```

预期：整封邮件不应只给有证书的收件人部分加密放行；应进入异常邮件或隔离，失败详情包含缺少证书的收件人地址。

### E15 - 国密 S/MIME

配置：

- 出站模式。
- 为 `cxc1234567892022@163.com` 签发并绑定 SM2 `SIGNING` 证书，证书带私钥且受信任。
- 为 `2416507029@qq.com` 签发并绑定 SM2 `ENCRYPTION` 证书，证书带私钥且受信任。
- `163.com` 域名策略：`encryptionPolicy=MANDATORY`，`preferredAlgorithm=GM_ONLY`，`signingEnabled=true`。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E15] 国密 S/MIME

这是一封 SealMail 扩展测试 E15。
```

预期：

- 审计中出现 `EMAIL_SIGNED`、`EMAIL_ENCRYPTED`。
- 处理轨迹或审计详情中 profile 为 `GM`。
- 生成的 S/MIME 使用国密算法族。公网 QQ Web 邮箱大概率无法直接阅读，这是兼容性限制，不代表网关加密失败。

### E16 - 加密 Profile 不匹配

配置方式一：

- 出站模式。
- `163.com` 域名策略：`encryptionPolicy=MANDATORY`，`preferredAlgorithm=GM_ONLY`。
- `2416507029@qq.com` 只保留 RSA `ENCRYPTION` 证书，不保留 SM2 加密证书。

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E16] 加密 Profile 不匹配

这是一封 SealMail 扩展测试 E16。
```

预期：邮件不投递，进入异常邮件或隔离；失败详情包含 `没有GM加密证书` 或 profile mismatch。也可以反向测试 `STANDARD_ONLY` 搭配仅 SM2 证书。

### E17 - 证书信任、吊销、过期

建议拆成三次执行，使用相同邮件主题后缀区分：

```text
[SealMail-E17-A] 收件人证书不受信任
[SealMail-E17-B] 收件人证书已吊销
[SealMail-E17-C] 收件人证书已过期
```

配置：

- 出站模式。
- `163.com` 域名策略：`encryptionPolicy=MANDATORY`，`preferredAlgorithm=STANDARD_ONLY`。
- A：对 `2416507029@qq.com` 的加密证书执行 `untrust`。
- B：对 `2416507029@qq.com` 的加密证书执行 `revoke`，必要时重新生成 CRL。
- C：导入或签发一张已经过期的测试加密证书并绑定。

预期：

- A/B/C 都不应被选为可用加密证书。
- 邮件不投递，失败详情分别指向不受信任、吊销或过期导致的证书不可用。
- 如果测试入站签名验签，也可对发件人签名证书做同样三类处理，预期为 `EMAIL_VERIFIED` 失败或异常邮件。

### E18 - Relay 故障

配置：

- 出站模式。
- 使用 T01 类似的普通邮件，不触发 DLP 隔离或阻断。
- 临时把 Relay 策略改到不可达端口，例如：

```json
{
  "enabled": true,
  "host": "127.0.0.1",
  "port": 9,
  "timeoutMs": 3000
}
```

邮件：

```text
From: cxc1234567892022@163.com
To: 2416507029@qq.com
Subject: [SealMail-E18] Relay 故障

这是一封 SealMail 扩展测试 E18。
```

预期：

- DLP 和路由通过后，Relay 阶段失败。
- 邮件进入异常邮件或 dead-letter 对应视图。
- 失败详情包含 `RELAY`、connection refused、timeout 或 Postfix/SMTP 连接失败。

完成后立即恢复 Relay 策略，否则后续实际邮件都会失败。

### E19 - 真实邮件认证 PASS

固定 `163.com -> qq.com` 不能完整测试此项。要证明 SPF/DKIM/DMARC 全 PASS，需要换成你控制 DNS 的域名，例如：

```text
From: sender@your-domain.example
To: receiver@your-domain.example 或 2416507029@qq.com
```

配置：

- 为自有发件域发布 SPF TXT，包含测试出口 IP 或 smart host。
- 为自有发件域发布 `selector._domainkey` DKIM TXT，公钥与网关私钥匹配。
- 发布 `_dmarc` TXT，建议先 `p=none`。
- 域名级 DKIM 策略启用，selector 与 DNS 一致。
- 入站测试时要确保真实来源 IP 由可信 SMTP 边界传给 SealMail，而不是只看到 `127.0.0.1`。

邮件主题：

```text
[SealMail-E19] 真实邮件认证 PASS
```

预期：

- 出站邮件有 DKIM 签名且公网可验签。
- 入站认证结果 SPF、DKIM、DMARC 均为 pass 或按预期 alignment 通过。
- `Authentication-Results` 记录完整结果。

### E20 - GM Edge 入站/出站

此项不能用普通 163/QQ Web 邮箱完成。需要部署 GM Edge 和支持 TLCP/国密 TLS 的对端。

配置要点：

```text
Inbound:  Partner -> Edge 2525 STARTTLS 或 2465 implicit TLS -> Postfix 2530 -> SealMail 10025
Outbound: Postfix transport_maps -> Edge 2526 -> Partner GM gateway
Admin:    127.0.0.1:2727 /health /status /metrics
```

Postfix 分流示例：

```bash
GM_DOMAINS=partner.example.cn sudo -E ops/postfix/configure-gm-edge.sh
```

入站邮件主题：

```text
[SealMail-E20-IN] GM Edge 入站
```

出站邮件主题：

```text
[SealMail-E20-OUT] GM Edge 出站
```

预期：

- `2525` STARTTLS 和 `2465` implicit TLS 均能完成国密握手。
- 入站经 `2530` 回到 Postfix，再进入 SealMail content filter。
- 出站目标域命中 `/etc/postfix/gm_transport` 并交给 Edge `2526`。
- Edge `/metrics` 和日志能看到对应连接、握手和转发计数。

## 11. 固定账号无法完整验证的项目

以下项目不是多写几封 `163.com -> qq.com` 邮件就能覆盖，必须换环境或加资源：

| 项目 | 原因 | 需要什么 |
| --- | --- | --- |
| SPF/DKIM/DMARC 全 PASS | 不能控制 `163.com` 和 `qq.com` DNS。 | 自有域名、可发布 DNS TXT、真实出口 IP 或可信 smart host。 |
| DKIM 公网验签 `d=163.com` | 无法发布 `sealmail-test._domainkey.163.com`。 | 自有发件域或企业域。 |
| 多收件人部分缺证书 | 固定收件方只有一个。 | 至少第二个收件地址。 |
| 多收件人 profile 不一致 | 需要两个收件人分别只有 RSA/SM2 能力。 | 两个以上测试收件人和对应证书。 |
| GM Edge 互通 | 163/QQ 不提供你的 TLCP/国密 TLS 对接链路。 | GM Edge、Kona/TLCP 客户端或伙伴 GM SMTP 网关。 |
| 公网收件端解密国密 S/MIME | 普通 Web 邮箱大多不支持 SM2/SM3/SM4 S/MIME。 | 支持国密 S/MIME 的客户端和私钥。 |
| CRL/OCSP 公网可达验证 | 本地默认 CRL/OCSP URL 不一定能被外部客户端访问。 | 对外可访问的 CRL/OCSP 地址和证书扩展。 |
| 登录、用户、权限、策略 CRUD、前端页面 | 这些是管理面能力，不由邮件内容触发。 | API/UI 自动化测试或人工验收清单。 |

## 12. 完整覆盖矩阵

| 能力 | 主流程 | 扩展覆盖 |
| --- | --- | --- |
| SMTP 接入、方向识别、Relay 成功 | T01-T07 | E01、E18、E20 |
| 出站 DLP 四种动作 | T01-T04 | E08-E10 |
| DLP 内容来源：`SUBJECT`、`BODY_TEXT` | T01-T04 | E08-E10 |
| DLP 内容来源：`HEADERS` | 未覆盖 | E03 |
| DLP 内容来源：`BODY_HTML` | 未覆盖 | E02 |
| DLP 内容来源：`ATTACHMENT_TEXT` | 未覆盖 | E04 |
| DLP 内容来源：`ATTACHMENT_PDF` | 未覆盖 | E05 |
| DLP 内容来源：`ATTACHMENT_ZIP_ENTRY` | 未覆盖 | E06 |
| DLP 内容来源：`ATTACHMENT_METADATA` | 未覆盖 | E07 |
| DLP policy/ruleGroup/priority/scope | 未覆盖 | E08-E09 |
| DLP `MONITOR`/`ENFORCE` | 未覆盖 | E08-E09 |
| DLP 内置检测器 | 未覆盖 | E10 |
| DLP 隔离放行 | T03 | E12-E13 |
| DLP 隔离拒绝、误报、批量操作 | 未覆盖 | E11-E12 |
| S/MIME RSA 签名/加密/解密/验签 | T02、T07 | E17 |
| S/MIME 国密 profile | 未覆盖 | E15-E16 |
| 证书缺失、不信任、吊销、过期 | T05 覆盖缺失 | E17 |
| 多收件人证书边界 | 未覆盖 | E14 |
| 出站 DKIM 签名 | T02 | E19 |
| 入站 SPF/DKIM/DMARC 失败/记录 | T06、E01 | E19 |
| 真实 SPF/DKIM/DMARC PASS | 未覆盖 | E19 |
| Relay 故障 | 未覆盖 | E18 |
| GM Edge 入站/出站 | 未覆盖 | E20 |
| 异常邮件 | T04-T06 | E13-E18 |

## 13. 测试后恢复建议

完成所有测试后，恢复为你希望长期运行的配置：

- `163.com` 和 `qq.com` 不应长期都标成本地域名；按实际网关所有权设置。
- 如果你不拥有 `163.com`，不要长期启用 `d=163.com` 的 DKIM 出站签名。
- 删除或停用所有 `TEST_*` DLP 测试规则、规则组、policy 和 selection。
- 恢复被禁用、吊销或取消信任的测试证书。若吊销不可逆，重新签发并绑定新证书。
- 恢复 Relay 策略，不要保留 E18 的不可达端口。
- 恢复 GM Edge 和 Postfix transport map，只保留真实需要走国密 Edge 的伙伴域。
- 全局邮件认证策略建议恢复为：

```json
{
  "enabled": true,
  "authservId": "sealmail-gateway",
  "trustedProxyMode": "DISABLED",
  "failureDefaultAction": "LOG_ONLY"
}
```

- 隔离策略按生产要求恢复，例如：

```json
{
  "maxRetentionDays": 30,
  "notificationEnabled": false,
  "releaseRequiresEncryption": false
}
```
