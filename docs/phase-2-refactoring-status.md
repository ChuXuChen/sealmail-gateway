# SealMail Gateway 阶段 2 重构状态

本文档记录史诗大重构执行到阶段 2 后的当前状态、已完成边界、验证结果和后续操作建议。

当前停止点：阶段 2 已完成，尚未进入阶段 3。下一步只有在明确确认后，才应开始阶段 3「强类型邮件处理上下文」。

## 1. 当前阶段状态

已完成阶段：

- 阶段 0：治理与重构基线。
- 阶段 1：核心行为锁定。
- 阶段 2：模块依赖边界治理。

未开始阶段：

- 阶段 3：强类型邮件处理上下文。
- 阶段 4：Spring Integration 编排替换旧 Pipeline 语义。
- 阶段 5：统一错误处理与隔离决策。
- 阶段 6：密码算法 Profile 化。
- 阶段 7：配置与数据职责治理。
- 阶段 8：审计与可观测性。
- 阶段 9：前端与管理 API 收敛。
- 阶段 10：旧实现删除与硬化。

## 2. 阶段 0 完成内容

阶段 0 只处理文档和治理基线，未改业务代码或数据库 migration。

已固化内容：

- `docs/refactoring-principles.md` 作为最高原则文档。
- `docs/epic-refactoring-plan.md` 作为阶段计划和验收依据。
- 当前模块依赖债务已记录。
- 旧 Pipeline 组件清单已记录。
- 每阶段验收命令模板、提交规则和禁止项已记录。

## 3. 阶段 1 完成内容

阶段 1 用 characterization tests 锁定旧行为，未主动替换主链路实现。

新增或补强的代表性测试：

- `sealmail-backend/sealmail-infra/src/test/java/com/sealmail/infra/mail/pipeline/DeadLetterHandlerTest.java`
- `sealmail-backend/sealmail-infra/src/test/java/com/sealmail/infra/mail/pipeline/MailPipelineFlowCharacterizationTest.java`

覆盖重点：

- 旧 Pipeline 成功路径。
- 失败处理路径。
- dead-letter 行为。
- relay、quarantine 和处理状态相关行为。

这些测试为后续阶段替换旧 Pipeline 语义提供回归保护。

## 4. 阶段 2 完成内容

阶段 2 只处理模块依赖边界、启动装配边界、具体实现下沉和架构防回退机制，未进入强类型邮件上下文、错误流重构、密码 profile 或前端收敛。

### 4.1 模块依赖边界

当前目标边界已经落地为：

```text
domain: 纯领域模型、领域服务、领域接口。
app: use case、事务、编排，依赖 domain，不承载密码库、token、MIME、文件系统等具体实现细节。
infra: 实现 domain 定义的端口，依赖 domain，不依赖 app。
web: controller/API，依赖 app，不直接依赖 domain 或 infra，也不承载具体技术实现。
boot: composition root，依赖 web/app/infra，只负责启动与装配。
```

关键变化：

- 移除 `infra -> app` 生产依赖。
- 移除 `web -> infra` 生产依赖。
- 新增 `sealmail-backend/sealmail-boot` 模块作为 composition root。
- 启动类和启动初始化器从 `web` 移入 `boot`。
- `web` controller 改为依赖 `app` 暴露的 use case、DTO 或 facade。
- `web` 中的 token、密码算法测试和本地装配实现已移出或改为 app 用例入口。
- `app` 中的 token 生成、证书密码学、MIME 组装、S/MIME 编解码和文件样例读取等具体实现已下沉到 infra。
- `infra` 适配器改为实现 `domain` 中定义的端口。

### 4.2 新增 domain 端口

阶段 2 将需要由 infra 实现的端口移动到 domain，避免 infra 反向依赖 app，也避免 app 直接持有具体技术实现。

代表性端口：

- `com.sealmail.domain.certificate.spi.CertificateCryptoPort`
- `com.sealmail.domain.certificate.spi.SmimeMessageCryptoPort`
- `com.sealmail.domain.security.PasswordEncoder`
- `com.sealmail.domain.security.AuthTokenPort`
- `com.sealmail.domain.quarantine.spi.QuarantineMailReleaseRelay`
- `com.sealmail.domain.dlp.config.DlpConfigPort`
- `com.sealmail.domain.mail.spi.OutboundMailSubmitter`
- `com.sealmail.domain.mail.spi.SmtpRelayProbe`
- `com.sealmail.domain.mail.spi.MailSampleStore`
- `com.sealmail.domain.mail.spi.MailMessageComposer`
- `com.sealmail.domain.mailauth.MailAuthConfigPort`
- `com.sealmail.domain.system.SystemSettingsProvider`

同时新增：

- `com.sealmail.domain.shared.exception.CodedDomainException`
- `Certificate` 领域模型中的算法族判断能力，例如 `supportsEncryptionSuite`、`isGmAlgorithmFamily` 和 `isStandardAlgorithmFamily`。

### 4.3 新增 app 层接口与 DTO

为使 web 不再直接访问 domain 或 infra，实现了 app 层用例和 DTO 边界。app 只负责编排 use case，不持有具体密码库、token 库、MIME 编码、Base64/charset 处理或文件系统读取细节。

覆盖范围：

- 用户与认证，包括 `LoginUseCase` 和 `LoginResponse`。
- 启动初始化。
- DLP 配置。
- 邮件认证配置。
- 系统设置。
- 邮件测试发送。
- S/MIME 与密码能力测试入口。
- 证书密钥材料响应 DTO，例如 `CryptoKeyMaterialResponse`。

代表性收敛：

- `SmimeOperationUseCase` 依赖 `SmimeMessageCryptoPort`，不再直接处理 S/MIME Base64、charset 或 BouncyCastle 细节。
- `MailTestUseCase` 依赖 `MailSampleStore` 与 `MailMessageComposer`，不再手工拼接 MIME 文本或读取本地样例文件。
- 认证登录通过 `AuthTokenPort` 生成访问令牌，app 不直接依赖 JJWT。

### 4.4 infra 适配器调整

infra 现在通过 domain 端口对外提供基础设施能力。具体技术实现归属 infra，不上浮到 app 或 web。

代表性调整：

- `BcCertificateCryptoPort` 实现 `CertificateCryptoPort`。
- `SmimeMessageCryptoAdapter` 实现 `SmimeMessageCryptoPort`。
- `JjwtAuthTokenPort` 实现 `AuthTokenPort`。
- `DlpConfigService` 实现 `DlpConfigPort`。
- `MailAuthConfigService` 实现 `MailAuthConfigPort`。
- `OutboundMailGateway` 实现 `OutboundMailSubmitter`。
- `SmtpRelayClient` 实现 `SmtpRelayProbe`。
- `FileMailSampleStore` 实现 `MailSampleStore`。
- `MimeMailMessageComposer` 实现 `MailMessageComposer`。
- 新增 `SystemSettingsProviderImpl`。
- `BCryptPasswordEncoder` 改为实现 domain 中的 `PasswordEncoder`。
- `QuarantineMailReleaseRelayImpl` 改为实现 domain 中的 `QuarantineMailReleaseRelay`。

### 4.5 boot composition root

新增模块：

- `sealmail-backend/sealmail-boot`

代表性文件：

- `sealmail-backend/sealmail-boot/src/main/java/com/sealmail/boot/SealMailGatewayApplication.java`
- `sealmail-backend/sealmail-boot/src/main/java/com/sealmail/boot/init/CertificateInitializer.java`
- `sealmail-backend/sealmail-boot/src/main/java/com/sealmail/boot/init/UserAccountInitializer.java`

web 中已删除启动与本地装配职责：

- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/SealMailGatewayApplication.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/init/CertificateInitializer.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/init/UserAccountInitializer.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/security/LocalAuthService.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/security/JwtTokenFilter.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/security/JwtTokenProvider.java`
- `sealmail-backend/sealmail-web/src/main/java/com/sealmail/web/controller/v1/SM2CryptoTestController.java`

web 中保留的入口均为应用用例适配：

- `BearerTokenAuthenticationFilter` 只负责 HTTP bearer token 提取与认证上下文设置。
- `CryptoCapabilityTestController` 只暴露通用密码能力测试 API，不持有具体算法实现。

### 4.6 架构防回退测试

新增架构测试覆盖 domain/app/infra/web/boot 的依赖边界：

- `sealmail-backend/sealmail-domain/src/test/java/com/sealmail/domain/architecture/DomainDependencyRulesTest.java`
- `sealmail-backend/sealmail-app/src/test/java/com/sealmail/app/architecture/AppDependencyRulesTest.java`
- `sealmail-backend/sealmail-infra/src/test/java/com/sealmail/infra/architecture/InfraDependencyRulesTest.java`
- `sealmail-backend/sealmail-web/src/test/java/com/sealmail/web/architecture/WebDependencyRulesTest.java`
- `sealmail-backend/sealmail-boot/src/test/java/com/sealmail/boot/architecture/BootDependencyRulesTest.java`

这些测试用于阻止以下依赖回潮：

- `domain` 依赖 app、web、infra 或 Spring/JPA 基础设施。
- `app` 依赖 web 或 infra。
- `infra` 依赖 app 或 web。
- `web` 依赖 domain 或 infra。
- `boot` 承载业务规则或被其他模块反向依赖。

额外防线：

- `app` 和 `web` 禁止直接引入 BouncyCastle、JJWT、JPA、Spring Data、Spring Integration、文件系统 API、Java security key/cert 类型、`Base64`、`StandardCharsets` 和 MIME header 字符串字面量。
- `web` 的 POM 不直接依赖 infra 或 domain；`app` 的 POM 不依赖 web 或 infra，也不直接引入具体基础设施库。
- 这些规则用于保证 web 只能依赖 app，app 只能编排 domain 端口，具体实现持续归属 infra。

## 5. 当前验证结果

已通过的后端验证命令：

```bash
mvn -pl sealmail-backend/sealmail-web -am test
mvn -pl sealmail-backend/sealmail-infra -am test
mvn -pl sealmail-backend/sealmail-boot -am test
mvn -f sealmail-backend/pom.xml test
```

最近一次完整确认已通过：

- `mvn -pl sealmail-backend/sealmail-boot -am test`
- `mvn -f sealmail-backend/pom.xml test`

完整 backend reactor 验证结果为成功：

- `sealmail-domain`：SUCCESS。
- `sealmail-infra`：SUCCESS。
- `sealmail-app`：SUCCESS。
- `sealmail-web`：SUCCESS。
- `sealmail-boot`：SUCCESS。

边界扫描结果：

- web 生产代码未发现 `com.sealmail.infra` 或 `com.sealmail.domain` 直接 import。
- app 生产代码未发现 `com.sealmail.web` 或 `com.sealmail.infra` 直接 import。
- infra 生产代码未发现 `com.sealmail.app` 或 `com.sealmail.web` 直接 import。
- web 生产代码中的认证过滤器和密码能力测试 controller 只依赖 app 暴露的接口。
- app 生产代码中的认证、邮件测试、证书和 S/MIME 用例只依赖 domain 端口，不直接依赖具体实现库。
- infra 生产代码保留 JJWT、BouncyCastle、MIME、Base64、文件系统和 Java security 细节。

注意：

- 单独运行不带 `-am` 的子模块测试可能受到本地仓库中旧模块 jar 影响；阶段验收应使用 reactor 命令。

已知非失败警告：

- Mockito dynamic agent warning。
- Surefire native output 导致的 corrupted channel warning。
- 现有 infra 测试中的预期错误日志。
- `ImportCertificateRequest` 现有 Lombok builder warning。

## 6. 当前工作树注意事项

当前工作树在本次阶段执行前已经存在大量未提交变更和删除项，其中包括：

- 本地 keystore、证书、私钥等敏感材料删除。
- IDE 文件删除。
- 前端和其他目录的既有删除或修改。
- 若干与后续阶段相关的既有 backend 修改。

处理原则：

- 不恢复已删除的密钥、证书、keystore 或本地敏感材料。
- 不回滚阶段外的既有用户改动。
- 若后续要拆分提交，应先人工区分阶段 0/1/2 变更与既有脏工作树内容。

## 7. 后续操作建议

进入阶段 3 前建议先做以下确认：

1. 确认阶段 2 的 Maven/POM、boot 模块和架构测试作为一个独立提交或一组可回滚提交落盘。
2. 确认不将本地密钥、证书、keystore 或真实凭据重新加入版本控制。
3. 再次运行：

```bash
mvn -f sealmail-backend/pom.xml test
```

4. 确认阶段 3 只处理强类型邮件处理上下文，不混入 Spring Integration 主链路替换。

阶段 3 的推荐入口：

- 定义 `MailProcessingContext`。
- 定义只承载 `mailProcessingContext` 的 `MailProcessingHeaders`。
- 定义 `MailProcessingDecision`。
- 定义 `MailProcessingErrorType`。
- 在入口直接创建强类型 context。
- 逐步迁移 `RoutingService` 和各 pipeline step 的状态读取逻辑，并废弃旧业务 header。

阶段 3 明确不应处理：

- 删除旧 Pipeline。
- 用 Spring Integration 完整替换 Pipeline 编排。
- 重写统一错误流。
- 重构 crypto algorithm profile。
- 修改数据库 migration。
- 收敛前端页面。

## 8. 停止结论

阶段 2 已完成并停止。当前代码已将核心模块依赖方向收敛到 domain/app/infra/web/boot 的目标边界：web 只依赖 app，app 只编排 domain 端口，具体 token、证书密码学、MIME、S/MIME、文件系统和基础设施实现均归属 infra，并通过架构测试防止主要反向依赖和具体实现细节回潮。

下一次继续时，应从阶段 3 开始，并严格限制在强类型邮件处理上下文范围内。
