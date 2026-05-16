# SealMail Gateway 史诗大重构计划

## 1. 计划目标

本文档定义 SealMail Gateway 后续史诗级重构的阶段路线、交付边界、验收标准和回滚策略。

本计划必须与 `docs/refactoring-principles.md` 同时使用。任何阶段的实现、评审和验收都不得违反该原则文档。

本计划的总目标是：

- 保持并固化 domain/app/infra/web/boot 的端口与适配器边界。
- 将邮件处理主链路迁移为由 Spring Integration 显式编排的稳定消息流。
- 将编排逻辑、业务策略、密码算法实现和基础设施访问彻底解耦。
- 建立强类型邮件处理上下文，减少散落字符串 header 和隐式状态。
- 统一错误处理、隔离决策、审计记录和处理轨迹。
- 将运行时业务配置沉入 PostgreSQL，YAML 只保留部署级配置和 secret 引用。
- 确保每个阶段都可独立构建、测试、评审和回滚。

## 2. 当前仓库快照

当前代码已经具备部分重构基础：

- 后端是 Maven 多模块结构：`sealmail-domain`、`sealmail-app`、`sealmail-infra`、`sealmail-web`。
- Spring Integration 已存在于 `sealmail-infra/src/main/java/com/sealmail/infra/config/IntegrationConfig.java`。
- 邮件处理主流程已集中在 `sealmail-infra/src/main/java/com/sealmail/infra/mail/pipeline/MailPipelineFlow.java`。
- PostgreSQL、Hibernate/JPA、Flyway 已经是主要持久化和 schema 演进机制。
- 现有测试覆盖了部分 domain、app、infra、crypto、DLP、SMTP、pipeline step 和 repository 行为。
- 敏感材料治理已有文档：`docs/security-local-material.md`。

当前主要架构债务：

- Maven 依赖方向与目标端口适配器边界不完全一致；当前存在 `web -> infra` 等直接耦合，启动装配职责也尚未与业务职责完全隔离。
- Spring Integration 已经存在，但核心编排仍依赖 `MailPipelineFlow`、`MailPipelineStep` 和 `PipelineResult` 的旧 Pipeline 语义。
- `PipelineStepTracker` 仍使用全局静态集合做步骤去重，属于必须废弃的状态控制方式。
- 多个步骤通过 `PipelineResult.success/failure/quarantine` 表达流程分支，错误和隔离决策未完全进入统一错误流。
- 邮件消息依赖大量字符串 header，例如 `mailEnvelope`、`processingId`、`preferredAlgorithm`、`mustEncrypt`、`quarantineRequired`、`relayHost`。
- `RoutingService` 同时承担路由、证书选择、算法判断、relay header 写入和处理记录创建，职责过重。
- 密码算法 profile 尚未成为稳定领域概念，RSA/SM2/AES/SM4 等判断仍散落在多个实现类中。
- YAML 中仍有本地默认账号/密码占位、DKIM 私钥 PEM 字段、运行时业务策略默认值等需要治理的内容。
- 前端和部分测试接口仍暴露底层 pipeline/crypto 概念，需要在后期收敛。

## 3. 总体执行规则

所有阶段必须遵守以下规则：

- 每个阶段只处理一类变化，不混入无关重构。
- 每个阶段必须有明确验收命令或验收样例。
- 每个阶段必须能通过单独提交回滚。
- 核心邮件路径必须先有 characterization test，再替换实现。
- 不修改已发布 Flyway migration，只能新增 migration。
- 不提交真实私钥、真实证书、keystore、数据库密码、SMTP 密码、JWT secret 或生产凭据。
- 不通过临时跨层依赖绕过架构边界。
- 不在业务步骤中新增手动 quarantine、手动 relay、吞异常或伪造成功状态。
- 不新增全局静态集合控制邮件处理状态。

建议每个阶段的提交粒度：

- 1 个阶段可以拆成多个小提交。
- 每个提交都必须能编译。
- 阶段最终提交必须通过该阶段定义的验收命令。
- 数据库变更、后端编排变更、前端页面变更不得混在同一个提交中。

## 4. 目标架构

### 4.1 后端依赖边界

目标依赖边界固定为：

```text
domain: 纯领域模型、领域服务、领域接口。
app: use case、事务、编排，依赖 domain。
infra: 实现 domain 定义的端口，依赖 domain，不依赖 app。
web: controller/API，依赖 app。
boot: 组装 Spring Bean，依赖 web/app/infra。
```

约束：

- `domain` 承载聚合、值对象、领域服务、领域事件和领域接口，不依赖 app、web 或 infra。
- `app` 承载 use case、事务边界、编排、DTO 映射和应用端口，依赖 domain，不依赖 web 或 infra 实现。
- `infra` 承载 JPA、PostgreSQL、BouncyCastle、SMTP、Spring Integration、文件系统和外部系统适配，只依赖 domain，并实现 domain 定义的端口。
- `web` 承载 controller、API DTO、认证过滤器、输入校验和响应包装，只依赖 app 暴露的用例或应用服务。
- `boot` 是 composition root，只负责 Spring Bean 装配、模块扫描和应用启动；若暂未独立成模块，启动类所在模块也只能承担装配职责。

禁止的混乱依赖：

- 禁止 `domain` import app、web、infra、Spring MVC、Spring Integration、JPA 或具体 crypto/SMTP 实现。
- 禁止 `app` import web controller、HTTP request/response、web security filter、infra entity、infra repository 实现、pipeline flow 或具体 crypto/SMTP 实现。
- 禁止 `web` 直接调用 infra repository、JPA entity、pipeline step、crypto 实现、SMTP client 或数据库访问对象。
- 禁止 `infra` import app use case、app DTO、app mapper、app service、事务编排类、web controller、web DTO 或 web security filter。
- 禁止将需要 infra 实现的端口定义在 app；这类端口必须放在 domain。
- 禁止使用全局 component scan、ApplicationContext 手动取 Bean、ServiceLocator、反射、字符串类名、静态工具类等方式绕过依赖边界。
- 禁止用无明确职责的 `common`、`shared`、`utils` 包混放不同层的类型。

### 4.2 邮件处理目标流

入站目标流：

```text
mailInboundChannel
  -> route
  -> auth
  -> decrypt
  -> verify
  -> dlp
  -> relay/quarantine
```

出站目标流：

```text
mailOutboundChannel
  -> route
  -> dlp
  -> sign
  -> encrypt
  -> dkimSign
  -> relay/quarantine
```

隔离释放目标流：

```text
quarantineRelease
  -> route
  -> policy check
  -> optional sign/encrypt/dkimSign
  -> relay
  -> audit
```

错误目标流：

```text
errorChannel
  -> classify
  -> update processing state
  -> audit
  -> retry/quarantine/dead-letter
```

### 4.3 强类型上下文

目标上下文类型：

- `MailProcessingContext`
- `MailProcessingHeaders`
- `MailProcessingDecision`
- `MailProcessingErrorType`
- `CryptoProfile`
- `RelayProfile`
- `DlpDecision`
- `AuditTrace`

原则：

- payload 只表达邮件内容或稳定消息对象。
- headers 只放 Spring Integration 边界必要元数据。
- 业务状态进入强类型 context。
- 不在同一条消息流中频繁改变 payload 语义。

## 5. 阶段计划

## 阶段 0：治理与重构基线

目标：建立可执行的重构治理边界，避免后续阶段失控。

范围：

- 保持 `docs/refactoring-principles.md` 为最高原则文档。
- 将本计划纳入评审依据。
- 明确当前架构债务清单。
- 明确验收命令和提交规则。

实施步骤：

1. 保留 `docs/refactoring-principles.md`，新增并维护本计划。
2. 记录当前模块依赖边界与目标端口适配器边界不一致的问题。
3. 记录现有旧 Pipeline 组件清单。
4. 定义每阶段验收命令模板。
5. 明确禁止修改历史 migration 和禁止提交密钥材料。

验收标准：

- 文档存在且被后续任务引用。
- 不改业务代码。
- 不改数据库 migration。

建议验收命令：

```bash
git diff -- docs/refactoring-principles.md docs/epic-refactoring-plan.md
```

回滚方式：

- 删除或回滚本阶段新增文档。

### 阶段 0 当前基线记录

本节是阶段 0 的可执行基线。后续阶段开始前必须先确认本节仍与当前代码一致；若代码已演进，应先更新本节，再进入下一阶段。

当前模块依赖边界与目标边界的不一致：

- `sealmail-infra` 当前仍依赖 `sealmail-app`，与目标 `infra` 只依赖 `domain` 并实现 `domain` 端口不一致。
- `sealmail-web` 当前仍依赖 `sealmail-infra`，与目标 `web` 只依赖 `app` 暴露 API 不一致。
- 当前尚未独立 `boot` 模块，启动装配职责仍需要在后续阶段收敛为 composition root。
- `sealmail-infra` 当前引入 `spring-boot-starter-web`，需要在模块边界治理阶段确认是否属于装配/测试遗留依赖。

现有旧 Pipeline 组件清单：

- `MailPipelineFlow`
- `MailPipelineStep`
- `PipelineResult`
- `PipelineStepTracker`
- `SMIMEProcessor`
- `DeadLetterHandler`
- `DecryptStep`
- `VerifyStep`
- `MailAuthenticationStep`
- `DlpStep`
- `SignStep`
- `EncryptStep`
- `DkimSignStep`
- `RelayStep`
- `QuarantineStep`

阶段验收命令模板：

```bash
git status --short
mvn -pl sealmail-backend/sealmail-domain -am test
mvn -pl sealmail-backend/sealmail-app -am test
mvn -pl sealmail-backend/sealmail-infra -am test
mvn -pl sealmail-backend/sealmail-web -am test
cd sealmail-frontend && npm run build && npm run lint
git diff -- docs/refactoring-principles.md docs/epic-refactoring-plan.md
```

阶段提交规则：

- 阶段 0 只允许文档变更，不改业务代码，不改数据库 migration。
- 阶段 1 只允许新增或调整 characterization tests，不替换主实现。
- 阶段 2 只处理模块依赖、装配边界和架构防回退机制。
- 阶段 3 只处理强类型上下文和 header 兼容层。
- 阶段 4 只处理 Spring Integration 编排替换旧 Pipeline 语义。
- 阶段 5 只处理统一错误流、隔离决策和 dead-letter。
- 阶段 6 只处理密码算法 profile 化。
- 阶段 7 只处理配置与数据职责治理。
- 阶段 8 只处理审计与可观测性。
- 阶段 9 只处理前端与管理 API 收敛。
- 阶段 10 只处理旧实现删除与硬化。

禁止项确认：

- 禁止修改历史 Flyway migration；已发布环境只能新增 migration。
- 禁止在 migration、YAML、properties、测试资源或文档中写入真实私钥、真实证书、keystore、数据库密码、SMTP 密码、JWT secret 或生产账号凭据。
- 禁止在阶段性重构中恢复已删除的本地密钥材料。
- 禁止把无关删除、格式化或前端页面改动混入后端阶段提交。

## 阶段 1：核心行为锁定

目标：先证明旧行为，再迁移实现。

范围：

- 入站普通邮件。
- 入站解密邮件。
- 入站验签邮件。
- 邮件认证失败进入隔离。
- 出站签名邮件。
- 出站加密邮件。
- 出站 DLP 命中并隔离。
- DLP 隔离释放。
- relay 成功。
- relay 失败。
- 路由策略失败。

实施步骤：

1. 为 `MailPipelineFlow` 增加端到端式 characterization tests。
2. 为 `RoutingService` 固定当前路由决策、header 输出和处理记录创建行为。
3. 为 `QuarantineMailReleaseRelayImpl` 固定隔离释放路径。
4. 为 `DeadLetterHandler` 和 `errorChannel` 固定当前失败行为。
5. 为审计事件补充关键断言：路由、DLP、隔离、relay、释放。
6. 为 RSA 和 SM2 双路径补充最小覆盖样例。

建议测试位置：

- `sealmail-infra/src/test/java/com/sealmail/infra/mail/pipeline/`
- `sealmail-infra/src/test/java/com/sealmail/infra/mail/`
- `sealmail-infra/src/test/java/com/sealmail/infra/crypto/`

验收标准：

- 新增测试在旧实现上通过。
- 不改变主实现行为。
- 测试能描述 payload、headers、处理状态、隔离记录、审计事件、relay 行为。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
mvn -pl sealmail-backend/sealmail-app -am test
```

回滚方式：

- 只回滚新增测试，不影响主实现。

## 阶段 2：模块依赖边界治理

目标：将模块依赖逐步收敛到 domain/app/infra/web/boot 的端口适配器边界，并建立防回退机制。

范围：

- Maven POM 依赖。
- Spring component scan 边界。
- 启动装配边界。
- domain 与 infra 之间的端口接口归属。
- web 对 infra 的直接依赖。

实施步骤：

1. 盘点当前 POM 依赖方向。
2. 识别 `web` 直接注入 infra 类型的位置。
3. 识别 `infra` 直接依赖 app use case、app DTO、app mapper 或 app service 的位置。
4. 为跨层调用引入稳定端口接口，并放入 domain。
5. 调整 POM，使 app 不依赖 infra，infra 不依赖 app，二者只能通过 domain 端口衔接。
6. 调整 web 和 boot 装配，使 web 不直接依赖 infra 业务实现。
7. 收敛 `@SpringBootApplication(scanBasePackages = "com.sealmail")` 这类全局扫描。
8. 增加架构测试或 Maven enforcer 规则，阻止反向依赖回潮。
9. 增加禁止清单覆盖：domain 不得依赖 Spring/JPA/infra，app 不得依赖 web/infra 实现，web 不得依赖 infra 实现，infra 不得依赖 app/web。

高风险点：

- Spring Boot 启动类和 JPA entity scan 可能暂时需要特殊装配。
- repository 实现和 domain repository 接口之间的方向必须谨慎处理。
- 如果现有模块结构暂时无法独立出 boot 模块，应先将启动类约束为 composition root，再逐步拆分。

验收标准：

- Maven reactor 编译通过。
- 模块间依赖符合阶段目标。
- 新增架构约束测试通过。
- web 层不再直接使用邮件 pipeline、crypto、persistence 实现类。
- 不存在通过全局扫描、ApplicationContext 手动取 Bean、ServiceLocator、静态工具类或无职责 common 包绕过边界的新代码。

建议验收命令：

```bash
mvn test
```

回滚方式：

- POM 和装配变更集中提交，失败时整体回滚该阶段。

## 阶段 3：强类型邮件处理上下文

目标：引入强类型上下文，降低字符串 header 和隐式状态依赖。

范围：

- 邮件 envelope。
- 处理 ID。
- 邮件方向。
- 路由决策。
- 证书选择结果。
- crypto profile。
- DLP 决策。
- relay profile。
- quarantine 信息。
- audit trace。

实施步骤：

1. 在合适层定义 `MailProcessingContext`。
2. 定义 `MailProcessingHeaders`，集中管理 Spring Integration header 名称。
3. 定义 `MailProcessingDecision`，替代松散的 `quarantineRequired`、`mustEncrypt`、`encryptionEnabled` 等状态。
4. 定义 `MailProcessingErrorType`，用于错误分类。
5. 定义 `CryptoProfile`，先作为上下文元数据，不立即改算法实现。
6. 在消息入口将旧 header 转换成 context。
7. 在消息出口保留兼容 header，保证旧步骤仍可运行。
8. 分批将 pipeline step 从读字符串 header 改为读 context。

迁移顺序：

1. `RoutingService`
2. `DlpStep`
3. `DecryptStep`
4. `VerifyStep`
5. `SignStep`
6. `EncryptStep`
7. `DkimSignStep`
8. `RelayStep`
9. `QuarantineStep`

验收标准：

- 字符串 header 名称集中在 `MailProcessingHeaders`。
- 核心步骤不再散落硬编码 header 名称。
- 旧 behavior tests 全部通过。
- 兼容层仍保证旧 flow 可运行。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

回滚方式：

- 保留旧 header 兼容层，任何一个步骤迁移失败时只回滚该步骤。

## 阶段 4：Spring Integration 编排替换旧 Pipeline 语义

目标：让 Spring Integration 成为邮件处理流的唯一编排机制。

范围：

- `MailPipelineFlow`
- `MailPipelineStep`
- `PipelineResult`
- `PipelineStepTracker`
- inbound/outbound/quarantine/relay flows

实施步骤：

1. 将每个处理步骤改造成明确的 handler、transformer、router 或 service activator。
2. 每个步骤成功时返回更新后的 message/context。
3. 每个步骤失败时抛出受控异常，交给 error flow。
4. 使用 Spring Integration router 表达 relay/quarantine 分支。
5. 使用 wire tap 或专用 handler 记录审计和处理轨迹。
6. 将 `PipelineResult.success/failure/quarantine` 从主链路移除。
7. 将 `PipelineStepTracker.executeWithTracking` 替换为 Spring Integration 生命周期中的追踪 handler。
8. 删除全局静态去重集合；如果需要幂等，使用 processing state 或数据库唯一约束。

推荐迁移路径：

1. 先迁 outbound happy path：`route -> dlp -> sign -> encrypt -> dkimSign -> relay`。
2. 再迁 outbound quarantine path。
3. 再迁 inbound happy path：`route -> auth -> decrypt -> verify -> dlp -> relay`。
4. 再迁 inbound quarantine path。
5. 最后迁隔离释放路径。

验收标准：

- 主邮件流不再依赖 `PipelineResult` 表达流程状态。
- 业务步骤不再手动发送 quarantine 或 relay channel。
- `MailPipelineFlow` 中深层嵌套路由被拆解为清晰的 Spring Integration flow。
- 旧 Pipeline 类要么删除，要么只作为短期兼容适配器存在。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

回滚方式：

- 每次只迁一条路径。
- 保留旧 flow bean 的可切换开关，阶段完成后再删除。

## 阶段 5：统一错误处理与隔离决策

目标：所有邮件处理异常进入统一错误流，隔离、重试、dead-letter 由错误流集中决策。

范围：

- `errorChannel`
- `DeadLetterHandler`
- 业务步骤异常。
- relay 异常。
- DLP 异常。
- crypto 异常。
- route 异常。
- quarantine 持久化异常。

实施步骤：

1. 定义 `MailProcessingException` 和错误类型枚举。
2. 将 crypto、DLP、relay、route、auth 异常分类。
3. 建立 `MailErrorFlow`，集中处理错误消息。
4. 错误流负责更新 `MailProcessing` 状态。
5. 错误流负责写审计。
6. 错误流负责决定 quarantine、retry 或 dead-letter。
7. 业务步骤中移除吞异常和返回 failure result 的逻辑。
8. 为每类错误补测试。

验收标准：

- 业务步骤不再伪造成功或吞异常。
- relay 失败、crypto 失败、DLP 失败都能进入统一错误流。
- 隔离记录包含错误类型、错误详情、processing id 和原始邮件内容。
- dead-letter 场景可观测。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
```

回滚方式：

- 保留旧 failure-to-quarantine 兼容逻辑到该阶段全部测试通过后再删除。

## 阶段 6：密码算法 Profile 化

目标：管道不再关心 RSA、SM2、AES、SM4 等具体算法分支。

范围：

- `SMIMEOperations`
- `BcSMIMEOperations`
- `SmimeAlgorithmSuites`
- `SignStep`
- `EncryptStep`
- `DecryptStep`
- `VerifyStep`
- `RoutingService`
- `RelayStep`
- 证书元数据和选择策略。

实施步骤：

1. 定义 `CryptoProfile`，至少包含 `STANDARD` 和 `GM`。
2. 将 `STANDARD` 映射到 `RSA + SHA-2 + AES`。
3. 将 `GM` 映射到 `SM2 + SM3 + SM4`。
4. 为证书建立 profile 元数据或 profile 推断服务。
5. 将证书选择逻辑从 `RoutingService` 抽出。
6. 将算法判断从 step 中迁移到 profile 选择器。
7. 将 `PreferredAlgorithm` 与 `CryptoProfile` 显式映射。
8. 建立混合证书、缺失证书、多收件人 profile 不一致的失败规则。
9. 增加 RSA 和 GM 双路径测试。

验收标准：

- 邮件管道只读 `CryptoProfile`，不硬编码 RSA/SM2/AES/SM4 分支。
- RSA 和 GM 两条路径边界清晰。
- 多收件人无法共享 profile 时明确失败并进入统一错误流。
- 旧 S/MIME 兼容测试通过。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-domain -am test
mvn -pl sealmail-backend/sealmail-infra -am test
```

回滚方式：

- 保留旧算法推断服务作为 fallback，单独回滚 profile 选择器。

## 阶段 7：配置与数据职责治理

目标：YAML 只管部署级配置，PostgreSQL 管运行时业务配置。

范围：

- 域名策略。
- 证书绑定。
- DLP 规则。
- 邮件认证策略。
- relay 策略。
- 隔离策略。
- 用户、角色、权限。
- secret 引用。

实施步骤：

1. 梳理 `application.yml` 中的运行时业务配置。
2. 确认 PostgreSQL 已有表是否足够承载这些配置。
3. 对缺失模型新增 Flyway migration。
4. 将 DKIM 私钥 PEM 配置改为 secret/path 引用，避免明文存储入口扩散。
5. 将 relay 策略迁移到数据库或策略服务，YAML 只保留默认 fallback。
6. 将隔离策略迁移到数据库，YAML 只保留安全默认值。
7. 将邮件认证策略完全通过 CRUD 修改。
8. 更新前端管理页面和 API。
9. 增加配置变更审计。

验收标准：

- 不修改历史 migration。
- 新增 migration 可重复验证。
- YAML 不再保存运行时业务规则和明文敏感材料。
- 管理 API 可修改运行时配置。
- 关键配置变更有审计事件。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
mvn -pl sealmail-backend/sealmail-web -am test
```

回滚方式：

- 新增表/列通过新增 migration 演进。
- 应用层保留读旧配置 fallback，确认迁移完成后再删除。

## 阶段 8：审计与可观测性补齐

目标：每封邮件具备完整处理轨迹，安全相关行为可审计。

范围：

- processing id。
- correlation id。
- route decision。
- certificate selection。
- sign/verify。
- encrypt/decrypt。
- DLP hit。
- quarantine。
- release。
- relay。
- config change。

实施步骤：

1. 统一生成和传播 `processingId` 与 `correlationId`。
2. 统一处理轨迹写入点。
3. 为证书选择写审计。
4. 为 crypto 操作结果写审计。
5. 为 DLP 命中写审计。
6. 为 relay 成功/失败写审计。
7. 为管理员释放隔离邮件写审计。
8. 为配置变更写审计。
9. 梳理日志级别，避免生产 DEBUG 泄露敏感信息。

验收标准：

- 核心邮件路径都能通过 processing id 查到完整轨迹。
- 审计记录不包含私钥、密码、完整敏感邮件正文。
- 失败路径和成功路径都有可追踪记录。

建议验收命令：

```bash
mvn -pl sealmail-backend/sealmail-infra -am test
mvn -pl sealmail-backend/sealmail-app -am test
```

回滚方式：

- 审计增强应以新增字段或新增事件为主，避免破坏旧记录。

## 阶段 9：前端与管理 API 收敛

目标：前端只依赖稳定管理 API，不暴露 infra/pipeline 内部概念。

范围：

- 域名策略页面。
- 证书页面。
- DLP 页面。
- 隔离页面。
- 邮件认证配置页面。
- 系统设置页面。
- 测试接口。

实施步骤：

1. 梳理前端 API client 和页面类型定义。
2. 将前端字段对齐 app/web DTO。
3. 移除对旧 pipeline header、底层 crypto 测试概念的依赖。
4. 对 SM2 测试接口进行 dev profile 限制或删除。
5. 确保敏感值只显示存在状态，不回显明文。
6. 管理操作结果对齐审计事件。
7. 统一错误响应和权限提示。

验收标准：

- `npm run build` 通过。
- `npm run lint` 通过或有明确已知问题清单。
- 前端不展示私钥、密码、keystore 内容。
- 管理页面能覆盖迁移后的运行时配置。

建议验收命令：

```bash
cd sealmail-frontend
npm run build
npm run lint
```

回滚方式：

- 前端 API 迁移按页面拆分提交。

## 阶段 10：旧实现删除与硬化

目标：删除兼容层和旧机制，完成架构硬化。

范围：

- 旧 Pipeline 类。
- 旧 `PipelineResult` 主链路。
- 静态步骤去重。
- 散落字符串 header。
- 手动 quarantine/relay 捷径。
- 测试性密钥生成接口。
- 不再使用的 YAML 配置项。
- 不再使用的 migration fallback。

实施步骤：

1. 删除不再被主链路引用的旧 Pipeline 类。
2. 删除 `PipelineStepTracker` 中的静态状态控制。
3. 删除旧 header 兼容层。
4. 删除业务步骤内部手动隔离和手动 relay 逻辑。
5. 删除不再使用的配置项和 properties。
6. 删除或限制测试控制器。
7. 增加敏感信息扫描脚本或检查说明。
8. 补全架构测试，禁止旧模式回归。

验收标准：

- 后端全量测试通过。
- 前端构建通过。
- 架构测试通过。
- 敏感信息扫描无真实密钥材料。
- Flyway validate 通过。
- 文档更新完成。

建议验收命令：

```bash
mvn test
cd sealmail-frontend
npm run build
npm run lint
```

回滚方式：

- 删除类和删除配置应单独提交，便于精确回滚。

## 6. 横向工作流

以下工作贯穿所有阶段。

### 6.1 测试策略

测试分层：

- domain unit test：纯领域规则。
- app use case test：权限、事务边界、DTO、业务流程。
- infra characterization test：SMTP、S/MIME、DLP、repository、IntegrationFlow。
- web controller test：API 合约、认证、错误响应。
- frontend build/lint：管理界面类型和构建。

核心路径必须有行为保护：

- 入站普通邮件。
- 入站解密与验签邮件。
- 出站签名与加密邮件。
- DLP 隔离邮件。
- 隔离邮件释放。
- relay 失败场景。

### 6.2 数据库策略

规则：

- 只新增 migration，不修改已发布 migration。
- migration 不写入生产密钥或生产账号凭据。
- schema 变更必须有 repository 或 use case 测试覆盖。
- 数据迁移必须可重复验证。

### 6.3 安全策略

规则：

- 私钥、真实证书、keystore、数据库密码、SMTP 密码、JWT secret 不进入仓库。
- 配置只保存环境变量占位、secret manager 引用、本地开发样例占位、非敏感默认值。
- 管理 API 不回显敏感值，只回显是否已配置、引用名称或路径。
- 日志和审计不记录明文私钥、密码或完整邮件正文。

### 6.4 回滚策略

每个阶段必须有回滚点：

- 文档阶段：回滚文档。
- 测试阶段：回滚新增测试。
- 模块依赖阶段：回滚 POM 与装配。
- 上下文阶段：保留旧 header 兼容层。
- 编排阶段：保留旧 flow 可切换入口直到验收完成。
- 错误流阶段：保留旧失败适配器直到所有失败场景通过。
- 数据库阶段：只前进式 migration，应用层保留 fallback。
- 前端阶段：按页面拆分提交。
- 删除阶段：旧类删除单独提交。

## 7. 推荐执行顺序

建议严格按以下顺序推进：

1. 阶段 0：治理与重构基线。
2. 阶段 1：核心行为锁定。
3. 阶段 2：模块依赖边界治理。
4. 阶段 3：强类型邮件处理上下文。
5. 阶段 4：Spring Integration 编排替换旧 Pipeline 语义。
6. 阶段 5：统一错误处理与隔离决策。
7. 阶段 6：密码算法 Profile 化。
8. 阶段 7：配置与数据职责治理。
9. 阶段 8：审计与可观测性补齐。
10. 阶段 9：前端与管理 API 收敛。
11. 阶段 10：旧实现删除与硬化。

核心风险区是阶段 4、阶段 5、阶段 6。执行这些阶段时，每次只迁移一条邮件路径，不要同时修改 inbound、outbound、quarantine release 和数据库模型。

## 8. 阶段完成定义

任一阶段完成必须满足：

- 阶段目标达成。
- 阶段内新增或修改的测试通过。
- 后端受影响模块可构建。
- 前端未被破坏，涉及前端时必须构建通过。
- 无真实敏感材料进入仓库。
- 无新增违反 domain/app/infra/web/boot 端口适配器边界的依赖。
- 无新增历史 migration 修改。
- 文档、测试和实现保持一致。

## 9. 当前开工注意事项

当前工作区存在较多未提交、删除和新增状态。正式实施任一阶段前，应先确认这些改动的归属：

- 不回滚用户已有改动。
- 不把无关删除混入重构提交。
- 不把历史密钥材料误恢复进仓库。
- 每个阶段开始前先查看 `git status --short`。
- 每个阶段结束时输出变更清单、验收命令和未解决风险。
