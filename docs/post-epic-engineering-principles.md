# SealMail Gateway 后史诗工程原则

## 1. 定位

史诗重构已结束，`docs/epic-refactoring-plan.md` 只保留历史上下文，不再作为阶段计划执行。本文件是后续功能、修复、评审和重构的项目级基线；若功能方案与本文件冲突，先保持架构边界、安全治理和邮件主链路，再调整方案。

相关文档：

- `docs/refactoring-principles.md`
- `docs/security-local-material.md`
- `docs/epic-refactoring-plan.md`，仅作历史参考

## 2. 后端边界

后端依赖方向是最高优先级约束：

```text
domain -> 无框架纯领域模型、领域服务、领域接口
app    -> use case、事务、业务编排，依赖 domain
infra  -> domain 端口实现，依赖 domain，不依赖 app/web
web    -> controller/API/security filter，依赖 app
boot   -> Spring Boot 启动与 Bean 装配，依赖 web/app/infra
```

持续禁止：

- `domain` 依赖 Spring MVC、Spring Integration、JPA、app、web、infra 或具体 crypto/SMTP 实现。
- `app` 依赖 web/infra 实现、HTTP request/response、JPA entity、pipeline flow 或具体 crypto/SMTP 实现。
- `web` 直接访问 infra repository/entity、数据库对象、pipeline step、crypto 实现或 SMTP client。
- `infra` 依赖 app use case、app DTO/mapper/service 或 web 类型。
- 将需要 infra 实现的端口定义在 app；这类端口必须定义在 domain。
- 用全局 component scan、`ApplicationContext` 手动取 Bean、ServiceLocator、反射、字符串类名、静态工具类或无职责 `common/shared/utils` 包绕过边界。

## 3. 现代 Spring Boot 原则

本仓库后端基线以 `sealmail-backend/pom.xml` 为准：当前是 Spring Boot `4.0.6`、Java `21`。升级 Boot、Spring、Jakarta、Hibernate、Spring Integration 或安全相关主版本时，必须作为独立变更处理，并用官方 migration guide 和测试闭环验证。

使用原则：

- 优先使用 Boot starter、parent/BOM、auto-configuration 和 Actuator/Micrometer 体系；不要随意覆盖 Boot 管理的依赖版本。
- `@SpringBootApplication` 和跨模块装配只放在 `boot`；业务模块用显式配置、端口和条件化 Bean 暴露能力，避免宽泛扫描破坏模块边界。
- 配置使用 `@ConfigurationProperties`、校验和元数据；避免散落 `@Value`。Profile 只表达环境差异，不承载业务策略。
- 新代码使用构造器注入；禁止字段注入、运行时查找 Bean、静态可变全局状态和隐藏副作用。
- 事务边界放在 app use case；web 只做协议适配，infra 只做外部系统适配。
- Spring Security 配置留在 web/boot；认证授权结果以 app/domain 可理解的类型传递，避免 domain 感知框架安全上下文。
- 使用 Actuator health、metrics、tracing、audit 和 Spring Integration graph 暴露运行状态；不要用临时管理接口或解析日志替代可观测性。
- 测试优先使用 slice test、契约/characterization test 和 Testcontainers；`@SpringBootTest` 只用于需要完整上下文的集成场景。
- 新代码遵循 Boot 4 的 Jakarta 生态；保留 `javax.*` 只能作为兼容适配，并隔离在明确边界内。

## 4. 邮件主链路

邮件处理必须由 Spring Integration channel、router、handler、transformer、service activator、wire tap 和 error channel 显式编排。

目标流：

```text
inbound:  mailInboundChannel -> route -> auth -> decrypt -> verify -> dlp -> relay/quarantine
outbound: mailOutboundChannel -> route -> dlp -> sign -> encrypt -> dkimSign -> relay/quarantine
release:  quarantineRelease -> route -> policy check -> optional sign/encrypt/dkimSign -> relay -> audit
error:    errorChannel -> classify -> state -> audit -> retry/quarantine/dead-letter
```

禁止恢复自定义 Pipeline 状态机作为主编排；禁止业务步骤手动发送 quarantine/relay、吞异常、伪造成功或用普通返回值表达失败。

## 5. 上下文、策略与算法

邮件处理状态必须进入强类型上下文，不散落在字符串 header 中。长期保持或演进：

- `MailProcessingContext`
- `MailProcessingHeaders`
- `MailProcessingDecision`
- `MailProcessingErrorType`
- `CryptoProfile`
- `RelayProfile`
- `DlpDecision`
- `AuditTrace`

payload 只表达邮件内容或稳定消息对象；headers 只放 Spring Integration 边界元数据；业务状态进入 context。管道只编排流程，不硬编码算法、证书路径、私钥路径、域名、邮箱、relay 主机或 DLP 规则。

密码路径保持清晰隔离：

- RSA + SHA-2 + AES
- SM2 + SM3 + SM4

算法选择由上下文、证书元数据、域名策略、DLP 策略、运行时配置和 `CryptoProfile` 决定，具体实现由 infra adapter 承担。

## 6. 配置、安全材料与数据库

禁止把以下内容写入 Git、普通 YAML/properties、SQL migration、测试资源或文档样例：

- 私钥、真实证书、keystore、DKIM 私钥
- 数据库/SMTP/JWT/生产账号密码
- 生产域名密钥材料和任何生产凭据

配置职责：

- YAML：部署级配置、环境变量占位符、secret 引用、外部挂载路径、非敏感默认值。
- PostgreSQL：域名策略、证书绑定、DLP 规则、邮件认证策略、relay 策略、用户、角色与权限等运行时业务配置。

数据库演进只能通过新增 Flyway migration；禁止修改已发布 migration、启动时隐式改 schema、依赖手工 SQL 或在 migration 中写入生产密钥/账号。

## 7. 错误、隔离与审计

异常必须进入 Spring Integration `errorChannel` 或明确的专用错误通道，由错误流完成分类、状态更新、审计、隔离、重试和 dead-letter。业务步骤不得自行吞异常、手动隔离、伪造成功或绕过统一错误处理。

必须持续审计和追踪：

- 路由、证书选择、签名/验签、加密/解密、DLP 命中
- 隔离、放行、relay 成功/失败、管理员释放隔离邮件
- 关键配置变更

每封邮件必须有可追踪的 correlation id 或 processing id。

## 8. 变更与验收

核心邮件路径和安全路径遵守“先证明旧行为，再迁移实现”。较大变更要用 characterization test、集成测试、验收样例或处理轨迹对比保护行为。

核心路径包括：

- 入站普通邮件、解密与验签、认证失败
- 出站签名、加密、DKIM 签名
- DLP 隔离、隔离释放、relay 成功与失败

提交边界：

- 每项较大变更只处理一类变化，数据库、后端编排、前端页面不要混入同一个无边界提交。
- 每个提交应可编译、可评审、可回滚，不混入无关删除、格式化或跨主题重构。
- 重要变更必须说明测试结果；无法运行时说明原因。
- 禁止放宽已经收紧的安全和运维默认值：CORS 不允许回退到 `*`，生产 health details 不允许 `always`，生产 SQL 输出保持关闭，旧自定义邮件 pipeline 抽象不得重新成为主链路。
- 禁止提交真实 secret、keystore、证书、私钥或生产凭据；数据库结构只通过新增 Flyway migration 演进，不修改已发布 migration。

推荐验收命令：

```bash
MAVEN_USER_HOME=/tmp/sealmail-m2 ./mvnw -Dmaven.repo.local=/tmp/sealmail-m2/repository -f sealmail-backend/pom.xml test
```

```bash
cd sealmail-frontend
npm run lint
npm run build
```

```bash
./ops/scan-sensitive-material.sh
git status --short
```

后续 DKIM/SPF/DMARC、DLP 扩展、MTA/SMTP 产品化、部署交付和前端重组，都必须在上述边界内重新制定清晰计划，不机械复活旧史诗阶段。
