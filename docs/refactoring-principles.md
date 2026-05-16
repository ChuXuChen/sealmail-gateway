# SealMail Gateway 重构原则

## 1. 适用范围

本文档定义 SealMail Gateway 在架构重构、邮件处理管道迁移、密码算法能力建设、安全配置治理和数据模型演进中的强制性原则。

所有重构工作均应以本文档作为设计、实现、评审和验收依据。

## 2. 技术基线

后端技术基线固定如下：

- Spring Boot 作为应用基础框架。
- Spring Integration 作为邮件处理流的唯一编排机制。
- PostgreSQL 作为主数据库。
- Hibernate/JPA 作为 ORM 与持久化访问基础。
- Flyway 作为数据库结构演进机制。

不得在重构过程中引入与上述基线冲突的替代性主框架。确需引入辅助组件时，应证明其不会削弱上述技术基线的边界与职责。

## 3. 后端依赖方向原则

后端模块依赖方向是最高优先级架构约束，必须严格保持端口与适配器边界：

```text
domain: 纯领域模型、领域服务、领域接口，不依赖 app、web 或 infra。
app: use case、事务、编排，依赖 domain，不依赖 web 或 infra 实现。
infra: 实现 domain 定义的端口，依赖 domain，不依赖 app 或 web，不被 domain/app 反向依赖其实现。
web: controller/API，依赖 app，不直接依赖 infra 实现。
boot: 组装 Spring Bean，依赖 web/app/infra，只承担启动与装配职责。
```

禁止任何反向依赖、环形依赖、跨层捷径依赖或通过启动类掩盖的业务耦合。

以下混乱依赖一律禁止：

- `domain` 依赖 `app`、`web`、`infra`、Spring MVC、Spring Integration、JPA entity、JPA repository、BouncyCastle 具体实现或 SMTP 实现。
- `app` 依赖 `web` controller、HTTP request/response、Spring Security filter、`infra` persistence entity、crypto 实现、SMTP client 或 Spring Integration flow。
- `web` 直接依赖 `infra` 的 repository 实现、JPA entity、pipeline step、crypto 实现、SMTP client、配置服务实现或数据库访问对象。
- `infra` 依赖 `app` use case、app DTO、app mapper、app service、事务编排类，或调用 `web` controller、读取 web DTO、依赖 web security filter。
- 除 `boot` 装配层外，任何模块不得通过全局 component scan、静态工具类、ServiceLocator、ApplicationContext 手动取 Bean、反射或字符串类名绕过依赖边界。
- 测试代码不得为了方便新增生产代码中的跨层依赖；需要测试适配时应使用 test fixture、mock、stub 或专用测试配置。
- 不得通过创建 `common`、`shared`、`utils` 等无明确职责的包来混放 web/app/domain/infra 类型。

具体要求：

- `domain` 承载聚合、值对象、领域服务、领域事件和领域接口，不得 import `app`、`web`、`infra`。
- `app` 承载 use case、事务边界、编排、DTO 映射和应用端口，允许依赖 `domain`，不得依赖 `web` 或 `infra` 实现。
- `infra` 承载 JPA、PostgreSQL、BouncyCastle、SMTP、Spring Integration、文件系统和外部系统适配，只允许依赖 `domain` 并实现 `domain` 定义的端口；不得依赖 `app`。
- `web` 承载 controller、API DTO、认证过滤器、输入校验和响应包装，只依赖 `app` 暴露的用例或应用服务，不得直接调用 `infra` 的 persistence、crypto、pipeline、SMTP 实现。
- `boot` 是 composition root，负责 Spring Bean 装配、模块扫描和应用启动；若暂未独立成模块，启动类所在模块也只能承担装配职责，不得放置业务规则。

业务运行时可以通过 domain 端口接口调用基础设施实现，但源码依赖必须保持上述边界。任何需要由 `infra` 实现的端口都必须定义在 `domain`，不得定义在 `app` 后再让 `infra` 依赖 `app`。任何功能实现、测试补丁、临时适配和迁移代码都不得破坏该边界。若现有代码存在违反该边界的问题，应作为架构债务显式记录并逐步消除，禁止新增同类问题。

## 4. 邮件处理流原则

邮件处理流程必须由 Spring Integration 的 channel、router、handler、transformer、service activator、wire tap 与 error channel 等机制编排。

目标流程应表达为稳定的消息流：

```text
inbound:
mailInboundChannel -> route -> auth -> decrypt -> verify -> dlp -> relay/quarantine

outbound:
mailOutboundChannel -> route -> dlp -> sign -> encrypt -> dkimSign -> relay/quarantine
```

以下实现方式应逐步废弃：

- 自定义 Pipeline 状态机。
- 深层嵌套的手工流程分支。
- 业务步骤内部手动发送 quarantine 或 relay channel。
- 通过普通返回值伪装失败状态。
- 使用全局静态集合进行步骤去重或状态控制。

## 5. 编排与算法解耦

消息管道只负责流程编排，不得依赖具体密码算法实现。

管道允许依赖的输入包括：

- 邮件处理上下文。
- 证书元数据。
- 域名策略。
- DLP 策略。
- 运行时配置。
- 算法 profile。

管道不得直接硬编码以下内容：

- RSA、SM2、AES、SM4 等具体算法分支。
- 证书路径。
- 私钥路径。
- 固定域名或邮箱。
- 固定 relay 主机。
- 固定 DLP 规则。

算法选择应由运行时上下文、证书类型、策略配置和算法 profile 共同决定。

## 6. 密码算法路径

系统必须明确支持两条纯净密码路径：

- RSA + SHA-2 + AES。
- SM2 + SM3 + SM4。

两条路径必须保持边界清晰，不得互相污染。

理想实现方式是通过统一的 `CryptoProfile`、证书元数据和策略配置完成自动选择，使邮件管道无需感知具体算法差异。

## 7. 敏感信息治理

以下内容禁止写入 Git 仓库、普通 YAML、properties、SQL migration 或其他明文配置文件：

- 私钥。
- 真实证书。
- keystore。
- 数据库密码。
- SMTP 密码。
- JWT secret。
- 生产域名密钥材料。
- 任何生产级账号凭据。

配置文件只能保存：

- 环境变量占位符。
- secret manager 引用。
- 本地开发样例占位符。
- 非敏感默认值。
- 外部挂载路径或引用名。

提交前必须确认仓库中不存在真实密钥材料和生产凭据。

## 8. 禁止硬编码

除稳定协议常量、枚举和错误码外，禁止硬编码业务规则、安全策略和部署参数。

以下内容必须配置化或数据化：

- 域名策略。
- 证书绑定。
- 算法 profile。
- relay 策略。
- DLP 规则。
- 隔离策略。
- 权限策略。
- 邮件认证策略。

常量应集中定义，不得散落在业务代码中。

## 9. 配置与数据职责划分

YAML 负责部署级配置，包括：

- 应用 profile。
- 服务端口。
- datasource 引用。
- SMTP 服务基础参数。
- secret 引用。
- 默认功能开关。

PostgreSQL 负责运行时业务配置，包括：

- 域名策略。
- 证书绑定。
- DLP 规则。
- 邮件认证策略。
- relay 策略。
- 用户、角色与权限。

运行时业务配置应通过 CRUD 修改数据库，不应要求运维直接修改 YAML。

## 10. 行为保护原则

每个重构阶段必须先证明旧行为，再迁移实现。

允许使用以下方式锁定旧行为：

- characterization test。
- 集成测试。
- 端到端场景样例。
- 明确的验收数据。
- 处理轨迹与审计记录对比。

不得在未证明旧行为的前提下替换核心实现。

## 11. 错误处理原则

邮件处理异常必须统一进入 Spring Integration `errorChannel` 或明确的专用错误通道。

错误处理职责应集中在错误流中，包括：

- 错误分类。
- 状态更新。
- 审计记录。
- 是否隔离的决策。
- 是否可重试的判断。
- dead-letter 处理。

业务步骤不得自行吞异常、手动隔离、伪造成功结果或绕过统一错误处理。

## 12. 消息上下文原则

邮件处理上下文必须强类型化。

应建立并统一使用：

- `MailProcessingContext`
- `MailProcessingHeaders`
- `MailProcessingDecision`
- `CryptoProfile`
- `MailProcessingErrorType`

payload、headers 与 context 的职责必须稳定。Spring Integration headers 只允许承载框架路由所需的少量边界元数据；邮件处理业务状态必须统一放入 `mailProcessingContext`。不得在同一条消息流中频繁改变 payload 语义，或依赖散落的字符串 header 传递关键状态。

## 13. 审计与可观测性

以下安全相关行为必须具备审计记录和处理轨迹：

- 路由决策。
- 证书选择。
- 签名与验签结果。
- 加密与解密结果。
- DLP 命中。
- 隔离。
- 放行。
- relay 成功或失败。
- 管理员释放隔离邮件。
- 关键配置变更。

每封邮件应具备可追踪的 correlation id 或 processing id。

## 14. 数据库演进原则

数据库结构演进必须通过 Flyway migration 完成。

禁止以下做法：

- 应用启动时隐式修改 schema。
- 依赖手工 SQL 作为运行前提。
- 在已发布环境中修改历史 migration。
- 在 migration 中写入生产密钥或账号凭据。

已发布版本只能通过新增 migration 演进。

## 15. 阶段化与可回滚

重构必须分阶段推进。每个阶段只处理一类变化，并满足以下条件：

- 可独立构建。
- 可独立测试。
- 可独立评审。
- 可独立回滚。

不得将 Spring Integration 迁移、密码算法重构、DLP 策略重构、前端重构和数据库重构混入同一个大提交。

## 16. 验收基准

任何重构阶段至少应满足：

- 后端测试通过。
- 前端构建不被破坏。
- 核心邮件路径行为与旧版本一致，除非变更被明确批准。
- 敏感信息未进入仓库。
- 新增配置、数据表和接口具备文档或测试覆盖。

核心邮件路径包括：

- 入站普通邮件。
- 入站解密与验签邮件。
- 出站签名与加密邮件。
- DLP 隔离邮件。
- 隔离邮件释放。
- relay 失败场景。

## 17. 总结

SealMail Gateway 重构的核心准则是：

> 严格保持 domain/app/infra/web/boot 的端口与适配器边界：domain 纯粹承载领域模型、领域服务和领域接口，app 承载 use case、事务和编排并只依赖 domain，infra 只依赖 domain 并实现 domain 定义的端口，web 只依赖 app 暴露 API，boot 只负责 Spring Bean 组装；使用 Spring Integration 编排稳定邮件消息流，使用 PostgreSQL 与 Hibernate 承载运行时业务配置，使用运行时注入的证书、策略和算法 profile 选择密码路径；严禁硬编码安全材料和业务规则；每次迁移必须先锁定旧行为，再替换实现。
