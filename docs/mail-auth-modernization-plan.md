# DKIM/SPF/DMARC 现代化改造计划

## 1. 目标

本计划定义 SealMail Gateway 后端 DKIM、SPF、DMARC 能力的下一阶段实现路线。目标不是在现有简化类上继续堆逻辑，而是按新架构建立清晰、可测试、可替换、可运营的邮件认证子系统。

最终状态：

- 入站邮件可以基于真实来源 IP、DKIM 签名、DMARC 策略做稳定认证。
- 出站邮件可以按域名策略完成 DKIM 签名。
- DNS 发布记录、DNS 健康检查、认证结果、失败处置和审计形成闭环。
- DKIM 私钥只通过文件路径或 secret reference 使用，不进入数据库明文字段。
- 实现与 `domain/app/infra/web/boot` 新架构完全适配，协议实现细节不泄漏到业务层。

## 2. 架构原则

1. 新架构优先。
   现有 `infra/mail/auth` 中的简化实现只能作为迁移参考和 characterization test 输入，不作为目标模块边界。

2. 端口适配器边界固定。
   - `domain`：邮件认证领域模型、策略、结果、领域端口。
   - `app`：用例、事务、权限前置、DTO 编排、策略发布流程。
   - `infra`：DNS 查询、DKIM 签名/验签、SPF/DMARC 解析、密钥解析、Spring Integration step adapter。
   - `web`：REST controller、请求响应 DTO、输入校验、权限注解。
   - `boot`：Bean 装配、第三方库 wiring、feature flag、启动期检查。

3. 协议能力优先使用成熟库或小而稳定的协议组件。
   自研代码只负责业务编排、策略选择、错误分类、审计、配置和适配。若必须自研协议片段，必须有 RFC fixture、fake DNS 和互操作样例测试。

4. 邮件原文是稳定边界。
   DKIM/SPF/DMARC 对 MIME 原文、SMTP envelope、真实来源 IP 敏感。任何 header/body 修改必须通过明确的邮件变换组件完成，不能在多个步骤里散落拼接字符串。

5. 认证结果是领域事实。
   SPF、DKIM、DMARC 的 pass/fail/none/temperror/permerror 及处置动作要进入强类型结果，不只存在于字符串头。

## 3. 目标模块划分

### 3.1 Domain

建议包：

```text
sealmail-domain/src/main/java/com/sealmail/domain/mailauth/
```

核心模型：

- `MailAuthPolicy`
- `DomainMailAuthPolicy`
- `DkimSigningPolicy`
- `DkimKeyRef`
- `DkimSelector`
- `SpfPublicationPolicy`
- `DmarcPublicationPolicy`
- `MailSourceIdentity`
- `AuthenticationResultSet`
- `AuthenticationMechanismResult`
- `DmarcAlignmentResult`
- `MailAuthDecision`
- `MailAuthFailureAction`
- `AuthenticationResultsHeader`

核心端口：

- `MailAuthPolicyRepository`
- `MailAuthDnsProbePort`
- `MailAuthVerifierPort`
- `DkimSigningPort`
- `DkimKeyResolverPort`
- `TrustedMailSourcePort`

要求：

- 不依赖 Spring、JPA、Jakarta Mail、DNS/JNDI、BouncyCastle 或 SMTP 具体库。
- 不保存 PEM 私钥。
- 不出现 `Message<?>`、Spring Integration header、JPA entity 或 Web DTO。

### 3.2 App

建议包：

```text
sealmail-app/src/main/java/com/sealmail/app/usecase/mailauth/
```

用例：

- `QueryMailAuthPolicyUseCase`
- `UpdateMailAuthPolicyUseCase`
- `QueryDomainMailAuthPolicyUseCase`
- `UpdateDomainMailAuthPolicyUseCase`
- `GenerateMailAuthDnsRecordsUseCase`
- `ProbeMailAuthDnsUseCase`
- `RotateDkimSelectorUseCase`
- `QueryMailAuthStatusUseCase`

要求：

- 负责权限、事务和领域端口编排。
- 不直接 import `infra` 实现或 JPA entity。
- 不直接解析 MIME、DNS、DKIM、SPF、DMARC。

### 3.3 Infra

建议包：

```text
sealmail-infra/src/main/java/com/sealmail/infra/mailauth/
```

适配器：

- `DnsLookupMailAuthProbe`
- `LibraryBackedMailAuthVerifier`
- `LibraryBackedDkimSigner`
- `SecretRefDkimKeyResolver`
- `PostfixTrustedSourceResolver`
- `AuthenticationResultsHeaderWriter`
- `MailAuthVerificationStep`
- `DkimSigningStep`

要求：

- 只实现 domain 端口。
- 协议库、DNS 查询、MIME 原文 canonicalization、secret resolver 都限制在 infra。
- Spring Integration step 只做消息适配，不承载协议规则。

### 3.4 Web

建议包：

```text
sealmail-web/src/main/java/com/sealmail/web/controller/v1/mailauth/
```

API：

- `GET /api/v1/mail-auth/policy`
- `PUT /api/v1/mail-auth/policy`
- `GET /api/v1/mail-auth/domains/{domain}/policy`
- `PUT /api/v1/mail-auth/domains/{domain}/policy`
- `GET /api/v1/mail-auth/domains/{domain}/dns-records`
- `POST /api/v1/mail-auth/domains/{domain}/dns-probe`
- `POST /api/v1/mail-auth/domains/{domain}/dkim/rotate-selector`
- `GET /api/v1/mail-auth/status`

要求：

- Controller 只依赖 app use case。
- 不返回私钥内容。
- secret ref、path、selector、DNS TXT、最近探测状态分开表达。

## 4. 入站认证目标流

目标链路：

```text
mailInboundChannel
  -> route
  -> resolve-source-identity
  -> verify-mail-auth
  -> write-authentication-results
  -> apply-mail-auth-policy
  -> decrypt
  -> verify-smime
  -> dlp
  -> relay/quarantine
```

关键点：

- `resolve-source-identity` 必须先确认真实来源 IP。
- 如果部署在 Postfix content-filter 后面，优先使用可信 `XFORWARD` 或受信任中继提供的信息。
- 只从可信中继接受来源覆盖信息；不信任公网来信携带的伪造 header。
- `Authentication-Results` 由统一 writer 写入。对于同一 `authserv-id` 的旧结果，应按策略移除、替换或标记，避免伪造内部认证结果。
- DMARC 失败不等于固定隔离。最终动作由对方策略、本地策略、失败类型、灰度比例和管理员配置共同决定。

## 5. 出站 DKIM 目标流

目标链路：

```text
mailOutboundChannel
  -> route
  -> dlp
  -> smime-sign
  -> smime-encrypt
  -> dkim-sign
  -> relay/quarantine
```

关键点：

- DKIM 签名必须发生在所有会改变 body 或被签 header 的步骤之后。
- 每个本地域名可以拥有独立 selector、key ref、签名 header 集、启停状态。
- 支持 selector 轮换：新 selector 发布 DNS 后启用，旧 selector 保留一段时间用于收件方缓存过渡。
- 签名失败不能静默吞掉。按策略记录审计、放行、隔离或失败。

## 6. 数据模型

新增或重塑为以下概念表。实际落库时只能新增 migration，不修改已发布 migration。

```text
mail_auth_policy
  id
  enabled
  authserv_id
  trusted_proxy_mode
  failure_default_action
  created_at
  updated_at
  version

mail_auth_domain_policy
  id
  domain_name
  enabled
  dkim_signing_enabled
  dkim_selector
  dkim_key_secret_ref
  dkim_key_path
  dkim_signed_headers
  spf_publish_enabled
  spf_use_a
  spf_use_mx
  spf_ip4
  spf_ip6
  spf_includes
  spf_all_policy
  dmarc_publish_enabled
  dmarc_policy
  dmarc_subdomain_policy
  dmarc_adkim
  dmarc_aspf
  dmarc_pct
  dmarc_rua
  dmarc_ruf
  created_at
  updated_at
  version

mail_auth_dns_probe
  id
  domain_name
  record_type
  expected_name
  expected_value_hash
  observed_value
  status
  detail
  checked_at
```

约束：

- DKIM 私钥字段只能是 path 或 secret ref。
- DNS probe 可存观测结果，但不要存私钥派生之外的敏感材料。
- 如果保留旧 `mail_auth_config`，应通过 adapter 做兼容读取，并规划迁移到域名级 policy。

## 7. 协议能力范围

### 7.1 DKIM

必须支持：

- `rsa-sha256`。
- relaxed/relaxed canonicalization。
- 多个 `DKIM-Signature` header 验证。
- selector/domain DNS 公钥解析。
- body hash mismatch、signature mismatch、key not found、syntax error 分类。
- 出站签名 header 集配置。
- selector 轮换。

可后续扩展：

- Ed25519 DKIM。
- 更完整 canonicalization 组合。
- ARC。

### 7.2 SPF

必须支持：

- `ip4`/`ip6`，包括 CIDR。
- `a`、`mx`、`include`、`redirect`、`exists`。
- DNS lookup 计数上限。
- `pass`、`fail`、`softfail`、`neutral`、`none`、`temperror`、`permerror`。
- 私网中继跳过策略，但必须明确记录为 `none` 或本地跳过结果。

必须解决：

- 后端看到 `127.0.0.1` 时不能直接代表公网来源。
- 真实来源 IP 必须来自可信 SMTP 边界，而不是任意邮件 header。

### 7.3 DMARC

必须支持：

- `adkim`、`aspf`。
- relaxed/strict alignment。
- `p`、`sp`、`pct`。
- 组织域判断。
- none/quarantine/reject 策略映射。
- 本地动作：仅记录、按策略隔离、强制隔离、强制放行。

暂不作为第一阶段目标：

- 聚合报告生成。
- 失败报告发送。
- BIMI。

## 8. DNS 发布与健康检查

DNS 记录生成必须由 domain policy 驱动：

- DKIM TXT：基于 selector 和公钥生成。
- SPF TXT：基于本地域名发送源策略生成。
- DMARC TXT：基于 DMARC 发布策略生成。

DNS 健康检查必须返回：

- 记录名。
- 期望值。
- 实际值。
- 是否匹配。
- TTL 或查询状态。
- 错误分类。
- 最近检查时间。

健康检查只证明 DNS 发布状态，不代表入站验证一定成功。

## 9. 审计与处理轨迹

必须审计：

- 邮件认证配置变更。
- DKIM selector 轮换。
- DNS probe。
- 入站认证失败并触发处置。
- 出站 DKIM 签名失败。
- 真实来源 IP 无法解析。

邮件处理轨迹必须记录：

- SPF 结果。
- DKIM 结果列表。
- DMARC 结果。
- 最终动作。
- 写入的 `Authentication-Results` 摘要。

## 10. 测试策略

测试分层：

- Domain 单元测试：策略、alignment、failure action、DNS record 生成。
- App 用例测试：权限、事务、配置更新、selector 轮换。
- Infra contract test：DNS fake、DKIM fixture、SPF fixture、DMARC fixture。
- Pipeline characterization test：入站认证失败隔离、记录模式放行、出站 DKIM 签名顺序。
- Migration test：旧配置可迁移，新 schema 可 validate。
- 安全测试：私钥不回显、不进入日志、不进入 API response。

验收命令：

```bash
MAVEN_USER_HOME=/tmp/m2 ./mvnw -Dmaven.repo.local=/tmp/m2/repository -f sealmail-backend/pom.xml test
```

如果只验证邮件认证后端：

```bash
MAVEN_USER_HOME=/tmp/m2 ./mvnw -Dmaven.repo.local=/tmp/m2/repository \
  -f sealmail-backend/pom.xml \
  -pl sealmail-domain,sealmail-app,sealmail-infra,sealmail-web \
  -am \
  -Dtest='*MailAuth*,*Dkim*,*Spf*,*Dmarc*' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

## 11. 实施阶段

### 阶段 0：行为保护与边界确认

交付：

- 为当前入站认证、出站 DKIM、DNS 记录生成补 characterization tests。
- 标记旧 `infra/mail/auth` 类为迁移对象。
- 明确真实来源 IP 当前缺口。

验收：

- 不改业务行为。
- 邮件认证相关测试可单独运行。

### 阶段 1：新领域模型与端口

交付：

- 新增 `domain/mailauth` 模型和端口。
- 新增 app use case 骨架。
- 新增 web API DTO 草案。
- 不接入主邮件流。

验收：

- domain/app/web 编译通过。
- 架构测试确保 app 不依赖 infra。

### 阶段 2：配置与数据库重塑

交付：

- 新增 migration。
- 新增 JPA entity 和 repository adapter。
- 支持从旧全局配置兼容读取或一次性迁移。
- 支持域名级 DKIM policy。

验收：

- Flyway validate 通过。
- 私钥只以 path/secret ref 表达。

### 阶段 3：协议适配器

交付：

- DNS lookup adapter。
- DKIM signing/verifying adapter。
- SPF adapter。
- DMARC adapter。
- `Authentication-Results` writer。

验收：

- fake DNS 测试覆盖 pass/fail/temp/perm。
- DKIM fixture 可验签。
- 出站签名失败有明确错误分类。

### 阶段 4：真实来源 IP 与 Postfix 边界

交付：

- 可信中继配置。
- `XFORWARD`/可信来源解析策略。
- 不可信 header 拒绝或忽略。
- 文档更新 Postfix 配置要求。

验收：

- content-filter 部署下 SPF 使用真实来源 IP。
- 私网跳过行为可审计。

### 阶段 5：接入邮件主链路

交付：

- 替换入站 `MailAuthenticationStep`。
- 替换出站 `DkimSignStep`。
- 接入强类型 `MailAuthDecision`。
- 认证失败动作进入统一隔离/错误流。

验收：

- 入站 DMARC quarantine/reject 可按本地策略隔离。
- 记录模式下失败邮件可放行但有审计。
- 出站签名顺序稳定在 S/MIME 之后、relay 之前。

### 阶段 6：DNS 健康检查与运营化

交付：

- DNS probe API。
- 最近探测结果持久化。
- 状态摘要。
- 前端可复制 DNS TXT 的稳定数据结构。

验收：

- 管理员能看到 DKIM/SPF/DMARC 发布状态。
- DNS 错误能明确分类。

### 阶段 7：旧实现下线

交付：

- 删除或封存旧 `infra/mail/auth` 简化类。
- 删除旧全局配置入口或改为兼容 facade。
- 更新文档和架构测试。

验收：

- 主链路只使用新端口和新 adapter。
- 旧实现不再被 Spring 装配。

## 12. 风险与决策点

主要风险：

- SPF 来源 IP 不可信会导致误判。
- DKIM 对 MIME 原文极其敏感，任何换行、折叠、header 改写都会破坏签名。
- 多域名 DKIM key 管理会增加 secret 生命周期复杂度。
- DMARC `reject` 直接隔离可能影响业务邮件，需要默认以 `none` 或记录模式灰度。

关键决策点：

- 是否引入第三方 mail-auth 协议库。
- 是否一阶段就支持多域名独立 DKIM key。
- Postfix 侧是否强制启用 XFORWARD。
- 旧 `mail_auth_config` 是兼容读取还是迁移后废弃。

推荐决策：

- 第一版企业可交付必须支持多域名独立 DKIM policy。
- SPF 真实 IP 必须在接入主链路前完成。
- DMARC 默认本地动作使用记录模式，管理员确认后再按策略隔离。
- 旧实现不继续扩大，只作为测试样例来源。

## 13. 完成标准

后端完成标准：

- 新邮件认证模型位于 `domain/mailauth`。
- 配置和 DNS 记录生成通过 app use case 暴露。
- Web controller 不直接依赖 infra。
- 入站 SPF/DKIM/DMARC 结果进入强类型处理轨迹和 `Authentication-Results`。
- 出站 DKIM 可按域名启用、禁用和轮换 selector。
- DMARC 失败可以按本地策略放行、记录或隔离。
- 协议失败路径有错误分类和审计。
- 私钥不进入数据库明文字段、API response 或日志。
- 邮件认证相关测试覆盖核心协议路径、配置路径和主链路路径。
