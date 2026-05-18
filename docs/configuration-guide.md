# SealMail Gateway 配置流程教程

本文把 SealMail Gateway 的配置按实际落地顺序串起来。项目里的配置来源很多，先记住四类边界：

- **启动环境变量**：数据库、JWT、Spring profile、前端代理目标、后端 keystore 路径等。
- **后端 YAML 默认值**：`sealmail-backend/sealmail-infra/src/main/resources/application.yml`。
- **数据库运行时策略**：Relay、隔离策略、国密 Edge、域名、DLP、DKIM/SPF/DMARC 等，通过管理界面或 `/api/v1/**` API 写入 PostgreSQL。
- **系统级邮件网关配置**：Postfix 的 `main.cf`、`master.cf`，以及可选的独立 `sealmail-edge.properties`。

不要把真实私钥、证书、keystore、数据库密码、SMTP 密码、JWT secret、DKIM 私钥或生产账号凭据提交进仓库。敏感材料只放环境变量、仓库外文件、secret manager 或本机 `.env`。

## 1. 准备基础组件

本地或部署机至少需要：

- Java 21。
- Maven Wrapper：使用仓库根目录的 `./mvnw`。
- PostgreSQL。
- Node.js 和 npm，用于前端。
- Postfix，用于真实邮件网关链路。只调试后端 API/前端时可先不配置。

先在仓库根目录确认构建基线：

```bash
./mvnw -f sealmail-backend/pom.xml test
```

## 2. 准备 PostgreSQL

默认后端 profile 是 `postgres`，默认连接：

```text
jdbc:postgresql://localhost:5432/sealmail
username: sealmail
password: 空
```

建议显式创建库和用户：

```bash
createdb sealmail
createuser sealmail
psql -d sealmail -c "ALTER USER sealmail WITH PASSWORD 'change-me';"
```

实际命令按你的 PostgreSQL 权限模型调整。后端启动时 `spring.flyway.enabled=true`，会自动执行 `sealmail-backend/sealmail-infra/src/main/resources/db/migration` 下的迁移。

如需启动前手动校验或迁移 Flyway：

```bash
SEALMAIL_FLYWAY_URL=jdbc:postgresql://localhost:5432/sealmail \
SEALMAIL_FLYWAY_USER=sealmail \
SEALMAIL_FLYWAY_PASSWORD=change-me \
./mvnw -f sealmail-backend/pom.xml -pl sealmail-infra -Pflyway-migrate flyway:validate
```

只在确认目标环境无误后执行：

```bash
SEALMAIL_FLYWAY_URL=jdbc:postgresql://localhost:5432/sealmail \
SEALMAIL_FLYWAY_USER=sealmail \
SEALMAIL_FLYWAY_PASSWORD=change-me \
./mvnw -f sealmail-backend/pom.xml -pl sealmail-infra -Pflyway-migrate flyway:migrate
```

## 3. 配置本地环境变量

后端启动硬门槛是 JWT secret。`jwt.secret-ref` 默认是 `env:SEALMAIL_JWT_SECRET`，因此必须配置 `SEALMAIL_JWT_SECRET`，或显式设置 `SEALMAIL_JWT_SECRET_REF` 指向可解析的 secret。

推荐在本机 `.env` 或 shell 中维护以下最小集合：

```bash
export SEALMAIL_DB_URL=jdbc:postgresql://localhost:5432/sealmail
export SEALMAIL_DB_USERNAME=sealmail
export SEALMAIL_DB_PASSWORD=change-me
export SEALMAIL_JWT_SECRET='replace-with-at-least-32-bytes-random-secret'
```

如果需要让系统签发/保存证书私钥后跨进程保留，还要配置后端 keystore：

```bash
export SEALMAIL_KEYSTORE_PATH=/absolute/path/outside/repo/sealmail-keystore.p12
export SEALMAIL_KEYSTORE_PASSWORD='change-me'
```

不配置 `SEALMAIL_KEYSTORE_PATH` 时，后端会使用进程内 keystore。它适合短期开发调试，不适合需要重启后保留私钥的环境。

Secret 引用支持三种形式：

```text
env:VARIABLE_NAME
file:/run/secrets/name
VARIABLE_NAME
```

其中 `env:` 和裸变量名都会从 Spring Environment 读取，`file:` 会读取文件内容。

## 4. 可选：初始化登录用户

默认配置没有内置明文管理员账号。启动时 `UserAccountInitializer` 会读取 `sealmail.auth.users`，并通过 `passwordSecretRef` 解析密码后写入 `user_account`。

开发环境可以追加 Spring 配置或用外部配置文件提供：

```yaml
sealmail:
  auth:
    users:
      - user-id: admin
        username: admin
        email: admin@example.test
        password-secret-ref: env:SEALMAIL_ADMIN_PASSWORD
        roles:
          - SUPER_ADMIN
        managed-domains:
          - example.test
```

然后设置：

```bash
export SEALMAIL_ADMIN_PASSWORD='change-me-now'
```

常用角色包括 `SUPER_ADMIN`、`ADMIN`、`PKI_ADMIN`、`DOMAIN_ADMIN`、`DOMAIN_MANAGER`、`AUDITOR`、`USER`。前端权限判断主要依赖这些角色；后端仍会校验业务用例里的权限。

## 5. 启动后端

推荐先打包再运行 boot jar：

```bash
./mvnw -f sealmail-backend/pom.xml -pl sealmail-boot -am -DskipTests package

SEALMAIL_DB_URL=jdbc:postgresql://localhost:5432/sealmail \
SEALMAIL_DB_USERNAME=sealmail \
SEALMAIL_DB_PASSWORD=change-me \
SEALMAIL_JWT_SECRET='replace-with-at-least-32-bytes-random-secret' \
java -jar sealmail-backend/sealmail-boot/target/sealmail-boot-1.0.0-SNAPSHOT.jar
```

默认监听：

```text
HTTP API: 0.0.0.0:8080
内部 SMTP content filter: 0.0.0.0:10025
Actuator health: /actuator/health
Swagger UI: /swagger-ui.html
```

常见后端启动项：

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `spring.profiles.active` | `postgres` | Spring profile。可用 `SPRING_PROFILES_ACTIVE=dev` 打开更详细 SQL/日志。 |
| `SEALMAIL_DB_URL` | `jdbc:postgresql://localhost:5432/sealmail` | PostgreSQL JDBC URL。 |
| `SEALMAIL_DB_USERNAME` | `sealmail` | 数据库用户名。 |
| `SEALMAIL_DB_PASSWORD` | 空 | 数据库密码。 |
| `SEALMAIL_JWT_SECRET` | 无 | JWT 签名密钥，必须能被 `jwt.secret-ref` 解析。 |
| `SEALMAIL_JWT_SECRET_REF` | `env:SEALMAIL_JWT_SECRET` | JWT secret 引用。 |
| `SEALMAIL_KEYSTORE_PATH` | 空 | 证书私钥 PKCS12 keystore 路径。 |
| `SEALMAIL_KEYSTORE_PASSWORD` | 空 | 后端 keystore 密码。 |
| `SEALMAIL_POSTFIX_ENVELOPE_FROM` | 空 | 通过 Postfix 回注/转发时的 envelope from 覆盖值。 |

仓库当前 `.env` 中出现的 `SEALMAIL_SMTP_KEYSTORE_PASSWORD_SECRET_REF`、`SEALMAIL_SMTP_KEY_PASSWORD_SECRET_REF`、`SEALMAIL_SMTP_PRIVATE_KEY_PASSWORD_SECRET_REF` 没有在当前主代码路径中直接绑定；使用前需要先确认是否来自历史配置或未完成能力。

## 6. 启动前端

前端位于 `sealmail-frontend`，Vite 默认端口 `5173`。前端 API 使用相对路径 `/api/v1/**`，开发服务器会代理到后端。

```bash
cd sealmail-frontend
npm install
VITE_API_PROXY_TARGET=http://localhost:8080 npm run dev
```

如果后端就在 `http://localhost:8080`，可以省略 `VITE_API_PROXY_TARGET`。生产部署时需要让前端站点的 `/api` 路径反向代理到后端，或用同域部署避免跨域复杂度。

登录接口：

```text
POST /api/v1/auth/login
body: {"username":"admin","password":"..."}
```

除 `/api/v1/auth/login`、`/api/v1/crl/**`、Swagger 和 health 外，其余 API 都需要 Bearer token。

## 7. 配置 Postfix 内容过滤链路

SealMail 的标准邮件链路是：

```text
外部/内部 SMTP -> Postfix:25 -> content_filter -> SealMail:10025
SealMail 处理后 -> Postfix:10026 或 10027 -> 后续投递
```

后端默认 `sealmail.postfix.enabled=true`，并期望：

```text
Postfix host: 127.0.0.1
SealMail content filter port: 10025
Postfix after-filter port: 10026
Postfix outbound port: 10027
```

在安装了 Postfix 的机器上执行：

```bash
sudo ops/postfix/configure-content-filter.sh
```

脚本会备份 `/etc/postfix/main.cf` 和 `/etc/postfix/master.cf`，然后：

- 设置 `content_filter=sealmail:[127.0.0.1]:10025`。
- 新增/修正 `sealmail` transport。
- 新增 `127.0.0.1:10026` 和 `127.0.0.1:10027` 回注 smtpd。
- 执行 `postfix check` 和 `postfix reload`。

如果 Postfix 与后端不在同一台机器，需要同时调整 Postfix 脚本生成的地址和后端 `sealmail.postfix.*` 配置，避免 10025/10026/10027 指向不一致。

## 8. 配置运行时策略

运行时策略存储在 PostgreSQL，不应该写死到 YAML。可以通过前端页面配置，也可以直接调用 API。以下 API 都需要登录后的 Bearer token。

### 8.1 Relay 策略

Relay 策略控制直接外发的 smart host。默认记录第一次读取时自动创建：

```text
enabled=false
host=localhost
port=25
timeoutMs=30000
allowUnconfiguredExternalRecipientDomains=false
```

API：

```text
GET /api/v1/runtime-policies/relay
PUT /api/v1/runtime-policies/relay
```

示例：

```bash
curl -X PUT http://localhost:8080/api/v1/runtime-policies/relay \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "host": "smtp.relay.example",
    "port": 587,
    "username": "sealmail",
    "passwordSecretRef": "file:/run/secrets/sealmail_relay_password",
    "timeoutMs": 30000,
    "envelopeFrom": "gateway@example.test",
    "allowUnconfiguredExternalRecipientDomains": false
  }'
```

密码只保存 secret 引用，不保存明文。

`allowUnconfiguredExternalRecipientDomains` 默认为 `false`。开启后，仅允许未配置的外部收件域继续使用全局 Relay 投递；发件人域仍必须是启用的本地域，已配置但停用的收件域仍会被拒绝，DLP、强制加密失败和隔离策略仍优先执行。

### 8.2 隔离策略

API：

```text
GET /api/v1/runtime-policies/quarantine
PUT /api/v1/runtime-policies/quarantine
```

默认值：

```text
maxRetentionDays=30
notificationEnabled=false
releaseRequiresEncryption=false
cleanup cron=0 0 * * * *
```

如需调整清理调度：

```properties
sealmail.quarantine-retention.cleanup-cron=0 0 2 * * *
```

### 8.3 域名策略

先配置本地域名和远程域名，否则系统很难判断邮件方向、加密策略和证书绑定范围。

API：

```text
POST /api/v1/domains
GET /api/v1/domains
PUT /api/v1/domains/{id}
```

创建本地域名示例：

```bash
curl -X POST http://localhost:8080/api/v1/domains \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "example.test",
    "localDomain": true,
    "encryptionPolicy": "ALLOW",
    "preferredAlgorithm": "AUTO",
    "signingEnabled": true,
    "dkimEnabled": true,
    "active": true
  }'
```

`encryptionPolicy` 可用 `NO_ENCRYPTION`、`ALLOW`、`MANDATORY`。`preferredAlgorithm` 可用 `AUTO`、`GM_ONLY`、`STANDARD_ONLY`。

### 8.4 DKIM/SPF/DMARC

全局 API：

```text
GET /api/v1/mail-auth/policy
PUT /api/v1/mail-auth/policy
GET /api/v1/mail-auth/status
```

域名级 API：

```text
GET /api/v1/mail-auth/domains/{domain}/policy
PUT /api/v1/mail-auth/domains/{domain}/policy
GET /api/v1/mail-auth/domains/{domain}/dns-records
POST /api/v1/mail-auth/domains/{domain}/dns-probe
POST /api/v1/mail-auth/domains/{domain}/dkim/rotate-selector
```

DKIM 私钥可以用 `dkimKeySecretRef` 或 `dkimKeyPath` 指定。推荐 secret 引用：

```json
{
  "enabled": true,
  "dkimSigningEnabled": true,
  "dkimSelector": "sealmail",
  "dkimKeySecretRef": "file:/run/secrets/dkim_example_test_private_key.pem",
  "dkimSignedHeaders": ["from", "to", "subject", "date", "message-id"],
  "spfPublishEnabled": true,
  "spfUseA": true,
  "spfUseMx": true,
  "spfIncludes": ["_spf.example.test"],
  "spfAllPolicy": "~all",
  "dmarcPublishEnabled": true,
  "dmarcPolicy": "quarantine",
  "dmarcPct": 100,
  "dmarcRua": "mailto:dmarc@example.test"
}
```

更新后用 DNS records API 生成 TXT 记录，再发布到权威 DNS，最后用 DNS probe API 验证。

### 8.5 DLP

DLP 配置同样在数据库里。主要资源：

```text
/api/v1/dlp/patterns
/api/v1/dlp/rules
/api/v1/dlp/rule-groups
/api/v1/dlp/policies
/api/v1/dlp/selections
/api/v1/dlp/edm-datasets
/api/v1/dlp/fingerprint-libraries
/api/v1/dlp/uba/senders
/api/v1/dlp/test
/api/v1/dlp/policies/{id}/simulate
/api/v1/dlp/quarantine
```

建议配置顺序：

1. 建规则：`PATTERN`、`EDM` 或 `FINGERPRINT`，并设置命中次数、证据数量、脱敏策略、动作、严重度、优先级。
2. 建规则组：把多个规则组合成可复用集合。
3. 建策略：指定方向、模式和规则组。
4. 建生效范围：按全局、域名、发件人、收件人等范围绑定策略或规则。
5. 用 test/simulate 验证，再放量启用。

`PATTERN` 规则覆盖正则、关键词和内置模板。旧 `REGEX`、`KEYWORD`、`BUILTIN` 请求会在保存后映射为 `PATTERN`；关键词会转义为正则，内置模板支持身份证、银行卡、API Key/Token、私钥块和中国手机号。

EDM 精确匹配先创建数据集，再导入敏感值。导入时系统只保存规范化后的 SHA-256 哈希和必要索引，不保存明文值；规则的 `pattern` 字段填写数据集 ID。

文档指纹先创建指纹库，再导入已抽取文本。系统按文本片段生成哈希指纹；规则的 `pattern` 字段填写指纹库 ID。第一版主要覆盖正文、HTML 和已能抽取文本的附件。

UBA 风险不会单独阻断邮件，而是在 DLP 命中后根据发件人历史基线、首次外部域、非常规时间、大附件和历史 DLP 命中提升风险。高风险可把 `WARN` 升级为 `MUST_ENCRYPT` 或 `QUARANTINE`；风险概览可通过 `/api/v1/dlp/uba/senders` 查询。

## 9. 可选：配置国密 GM Edge

GM Edge 是独立 Java 进程，用于 TLCP/国密 TLS 1.3 伙伴流量。它不直接读取后端数据库，而是读取 properties 文件。示例在：

```text
sealmail-backend/sealmail-edge/src/main/resources/sealmail-edge.example.properties
```

复制到仓库外：

```bash
cp sealmail-backend/sealmail-edge/src/main/resources/sealmail-edge.example.properties /etc/sealmail/sealmail-edge.properties
```

重点配置：

```properties
edge.inbound.enabled=true
edge.inbound.starttls-port=2525
edge.inbound.implicit-tls-port=2465

edge.outbound.enabled=true
edge.outbound.bind-address=127.0.0.1
edge.outbound.port=2526

edge.postfix.host=127.0.0.1
edge.postfix.port=2530

edge.tls.key-store=/run/secrets/sealmail-gm-edge.p12
edge.tls.key-store-password=${SEALMAIL_GM_EDGE_KEYSTORE_PASSWORD}
edge.tls.trust-store=/run/secrets/sealmail-gm-trust.p12
edge.tls.trust-store-password=${SEALMAIL_GM_EDGE_TRUSTSTORE_PASSWORD}

edge.outbound.routes=.partner.example.cn=gm-relay.partner.example.cn:2525
```

构建和启动：

```bash
./mvnw -f sealmail-backend/pom.xml -pl sealmail-edge -am -DskipTests package

SEALMAIL_GM_EDGE_KEYSTORE_PASSWORD='change-me' \
SEALMAIL_GM_EDGE_TRUSTSTORE_PASSWORD='change-me' \
./mvnw -f sealmail-backend/pom.xml -pl sealmail-edge \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.sealmail.edge.SealMailEdgeApplication \
  -Dexec.args=/etc/sealmail/sealmail-edge.properties
```

当前 `sealmail-edge` 模块产物不是包含所有依赖的 fat jar。生产部署成 systemd/service 时，需要让部署脚本提供 edge jar 加运行时依赖 classpath，或后续补独立发行包配置。

管理端口默认：

```text
http://127.0.0.1:2727/health
http://127.0.0.1:2727/status
http://127.0.0.1:2727/metrics
```

若要让 Postfix 把特定域名交给 Edge outbound：

```bash
GM_DOMAINS=partner.example.cn sudo -E ops/postfix/configure-gm-edge.sh
```

该脚本会：

- 新增 `smtp-gm` transport。
- 新增 `127.0.0.1:2530`，用于 GM inbound 经 Edge 解密后回到 Postfix，再进入 SealMail content filter。
- 维护 `/etc/postfix/gm_transport` 并配置 `transport_maps`。

后端也有 `/api/v1/runtime-policies/gm-edge` 用于管理 GM Edge 策略快照。当前独立 Edge 进程仍以 properties 文件为准，部署系统需要负责把数据库策略同步/渲染成 `sealmail-edge.properties` 并重启 Edge。

## 10. 配置证书和 CA

首次启动且证书库为空时，`sealmail.init.certs.enabled=true` 会自动创建 RSA 和 SM2 双栈内部 CA 及示例 end-entity 证书。

生产环境建议显式决定是否启用自动初始化：

```properties
sealmail.init.certs.enabled=false
```

内部 CA 常用配置：

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `sealmail.ca.crl-base-url` | `http://localhost:8080/api/v1/crl/` | 写入证书 CRL Distribution Point 的 URL 前缀。 |
| `sealmail.ca.default-root-validity-days` | `3650` | Root CA 默认有效期。 |
| `sealmail.ca.default-intermediate-validity-days` | `1825` | Intermediate CA 默认有效期。 |
| `sealmail.ca.default-end-entity-validity-days` | `365` | 终端证书默认有效期。 |
| `sealmail.security.crl.enabled` | `true` | 校验证书时启用 CRL。 |
| `sealmail.security.ocsp.enabled` | `true` | 校验证书时启用 OCSP。 |
| `sealmail.security.ocsp.timeout` | `10000` | OCSP 超时，毫秒。 |

如果对外签发证书，务必把 `crl-base-url` 改成客户端能访问的正式地址。

## 11. 配置清单

上线前逐项确认：

- PostgreSQL 库、用户、密码已创建，Flyway migration 已执行。
- `SEALMAIL_JWT_SECRET` 或 `SEALMAIL_JWT_SECRET_REF` 已配置，并且 secret 长度满足 HMAC key 要求。
- 管理员用户已通过 `sealmail.auth.users` 或其他方式创建。
- 后端 health 正常：`curl http://localhost:8080/actuator/health`。
- 前端 `/api` 代理指向后端。
- 本地域名、远程域名、证书绑定、邮件认证策略已配置。
- DKIM/SPF/DMARC TXT 记录已发布并通过 DNS probe。
- DLP 规则、策略、生效范围已用 test/simulate 验证。
- Postfix content filter 已配置，10025/10026/10027 端口方向一致。
- 敏感材料在仓库外，提交前运行 `ops/scan-sensitive-material.sh`。
- 如果启用 GM Edge，Edge properties、keystore/truststore、Postfix transport map 和 `/health` 都已验证。

## 12. 常见问题

### 启动时报 JWT secret 未配置

设置：

```bash
export SEALMAIL_JWT_SECRET='replace-with-at-least-32-bytes-random-secret'
```

或设置：

```bash
export SEALMAIL_JWT_SECRET_REF=file:/run/secrets/sealmail_jwt_secret
```

### 登录不了

确认 `sealmail.auth.users` 是否配置，`passwordSecretRef` 是否能解析，启动日志是否出现 `Seeded auth user into PostgreSQL`。如果用户已存在，initializer 不会重复覆盖所有属性，必要时通过用户管理 API 或数据库迁移处理。

### 后端启动但发信失败

先分层检查：

```bash
curl http://localhost:8080/actuator/health
postfix check
postconf -h content_filter
nc -vz 127.0.0.1 10025
nc -vz 127.0.0.1 10026
nc -vz 127.0.0.1 10027
```

再检查运行时 Relay 策略是否启用、Postfix `transport_maps` 是否覆盖了目标域名、域名配置是否把发件人域识别成本地域名。

### 证书或私钥重启后丢失

通常是没有配置 `SEALMAIL_KEYSTORE_PATH`，导致使用了进程内 keystore。配置仓库外的 PKCS12 路径和密码后重启。

### GM Edge 启动失败

检查 `sealmail-edge.properties` 是否存在，监听端口是否冲突，`edge.tls.key-store` / `trust-store` 文件是否可读，密码是否被实际展开。Edge 的 properties loader 不会自动读取后端数据库策略。
