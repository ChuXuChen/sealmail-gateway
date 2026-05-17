# SealMail Gateway 后史诗工程原则

## 1. 定位

史诗大重构已经结束，`docs/epic-refactoring-plan.md` 中的阶段路线不再作为当前执行计划使用。但该文档沉淀出的部分架构和工程原则仍然是后续功能开发、修复、扩展和评审的长期约束。

本文件提取这些仍需保持的原则，作为后续工作默认遵守的项目级工程基线。若与具体功能计划冲突，应优先保持本文件中的架构边界、安全治理和邮件主链路原则，再调整功能方案。

关联文档：

- `docs/epic-refactoring-plan.md`
- `docs/refactoring-principles.md`
- `docs/security-local-material.md`

## 2. 持续目标

后续所有后端和全栈变更应继续维护以下目标：

- 固化 `domain/app/infra/web/boot` 的端口与适配器边界。
- 保持邮件处理主链路由 Spring Integration 显式编排。
- 将业务编排、业务策略、密码算法实现和基础设施访问解耦。
- 使用强类型邮件处理上下文，避免散落字符串 header 和隐式状态。
- 统一错误处理、隔离决策、审计记录和处理轨迹。
- 运行时业务配置进入 PostgreSQL，YAML 只保留部署级配置、非敏感默认值和 secret 引用。
- 每项较大变更都能独立构建、测试、评审和回滚。

## 3. 后端依赖边界

后端模块依赖方向仍是最高优先级架构约束：

```text
domain: 纯领域模型、领域服务、领域接口。
app: use case、事务、编排，依赖 domain。
infra: 实现 domain 定义的端口，依赖 domain，不依赖 app 或 web。
web: controller/API，依赖 app。
boot: 组装 Spring Bean，依赖 web/app/infra，只承担启动与装配职责。
```

持续禁止：

- `domain` 依赖 app、web、infra、Spring MVC、Spring Integration、JPA 或具体 crypto/SMTP 实现。
- `app` 依赖 web controller、HTTP request/response、web security filter、infra entity、infra repository 实现、pipeline flow 或具体 crypto/SMTP 实现。
- `web` 直接调用 infra repository、JPA entity、pipeline step、crypto 实现、SMTP client 或数据库访问对象。
- `infra` 依赖 app use case、app DTO、app mapper、app service、事务编排类、web controller、web DTO 或 web security filter。
- 将需要 infra 实现的端口定义在 app；这类端口必须定义在 domain。
- 通过全局 component scan、ApplicationContext 手动取 Bean、ServiceLocator、反射、字符串类名、静态工具类或无职责 `common`/`shared`/`utils` 包绕过边界。

## 4. 邮件处理流

邮件处理主链路必须继续由 Spring Integration channel、router、handler、transformer、service activator、wire tap 和 error channel 等机制编排。

稳定目标流：

```text
inbound:
mailInboundChannel -> route -> auth -> decrypt -> verify -> dlp -> relay/quarantine

outbound:
mailOutboundChannel -> route -> dlp -> sign -> encrypt -> dkimSign -> relay/quarantine

quarantineRelease:
quarantineRelease -> route -> policy check -> optional sign/encrypt/dkimSign -> relay -> audit

error:
errorChannel -> classify -> update processing state -> audit -> retry/quarantine/dead-letter
```

持续禁止：

- 恢复自定义 Pipeline 状态机作为主编排机制。
- 在业务步骤内部手动发送 quarantine 或 relay channel。
- 通过普通返回值伪装失败状态。
- 吞异常后伪造成功。
- 使用全局静态集合进行步骤去重或邮件处理状态控制。

## 5. 强类型上下文

邮件处理状态必须进入强类型上下文，而不是散落在字符串 header 中。

长期保持使用或演进以下类型：

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
- 业务状态进入 context。
- 不在同一条消息流中频繁改变 payload 语义。

## 6. 编排与算法解耦

消息管道只负责流程编排，不得硬编码具体密码算法、证书路径、私钥路径、固定域名、固定邮箱、固定 relay 主机或固定 DLP 规则。

算法选择应由以下输入共同决定：

- 邮件处理上下文。
- 证书元数据。
- 域名策略。
- DLP 策略。
- 运行时配置。
- 算法 profile。

继续保持两条清晰密码路径：

- RSA + SHA-2 + AES。
- SM2 + SM3 + SM4。

两条路径不得互相污染。邮件管道不应感知具体算法分支，具体实现应由 `CryptoProfile`、证书元数据、策略和 infra adapter 承担。

## 7. 配置、安全材料与数据库演进

敏感材料治理仍然是强约束。以下内容禁止写入 Git、普通 YAML、properties、SQL migration、测试资源或文档样例：

- 私钥。
- 真实证书。
- keystore。
- 数据库密码。
- SMTP 密码。
- JWT secret。
- DKIM 私钥。
- 生产域名密钥材料。
- 任何生产级账号凭据。

配置职责：

- YAML 只负责部署级配置、环境变量占位符、secret 引用、外部挂载路径、非敏感默认值。
- PostgreSQL 负责运行时业务配置，例如域名策略、证书绑定、DLP 规则、邮件认证策略、relay 策略、用户、角色与权限。

数据库演进：

- 只能通过新增 Flyway migration 演进 schema。
- 禁止修改已发布 migration。
- 禁止应用启动时隐式修改 schema。
- 禁止依赖手工 SQL 作为运行前提。
- 禁止在 migration 中写入生产密钥或账号凭据。

## 8. 错误处理、隔离与审计

邮件处理异常必须统一进入 Spring Integration `errorChannel` 或明确的专用错误通道。

错误流负责：

- 错误分类。
- 状态更新。
- 审计记录。
- 隔离决策。
- 重试判断。
- dead-letter 处理。

业务步骤不得自行吞异常、手动隔离、伪造成功结果或绕过统一错误处理。

必须持续审计和追踪：

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

## 9. 行为保护与提交边界

较大变更仍应遵守“先证明旧行为，再迁移实现”的原则。核心邮件路径和安全相关路径变更前，应补充 characterization test、集成测试、验收样例或处理轨迹对比。

核心邮件路径包括：

- 入站普通邮件。
- 入站解密与验签邮件。
- 邮件认证失败路径。
- 出站签名与加密邮件。
- 出站 DKIM 签名路径。
- DLP 隔离邮件。
- 隔离邮件释放。
- relay 成功与失败。

提交边界：

- 每项较大变更只处理一类变化。
- 数据库变更、后端编排变更、前端页面变更不得混入同一个无边界提交。
- 每个提交应能编译。
- 阶段性工作最终必须有明确验收命令或验收样例。
- 不混入无关删除、格式化或跨主题重构。

## 10. 验收基准

后续重要变更至少满足：

- 后端相关测试通过，或明确说明无法运行的原因。
- 前端变更不破坏构建和 lint，或明确说明无法运行的原因。
- 核心邮件路径行为与既有版本一致，除非变更被明确批准并写入计划。
- 敏感信息未进入仓库。
- 新增配置、数据表、接口和安全行为具备文档或测试覆盖。

推荐后端验收命令：

```bash
MAVEN_USER_HOME=/tmp/m2 ./mvnw -Dmaven.repo.local=/tmp/m2/repository -f sealmail-backend/pom.xml test
```

推荐前端验收命令：

```bash
cd sealmail-frontend
npm run lint
npm run build
```

## 11. 对已结束史诗计划的处理

`docs/epic-refactoring-plan.md` 中的阶段编号、阶段顺序和历史基线记录只作为历史上下文，不再机械约束新的功能计划。

仍长期有效的是：

- 架构边界。
- 邮件流编排方式。
- 强类型上下文。
- 统一错误流。
- 安全材料治理。
- Flyway 演进规则。
- 行为保护和可回滚原则。
- 审计与处理轨迹要求。

后续新增能力，例如 DKIM/SPF/DMARC、DLP 扩展、MTA/SMTP 设置产品化、部署交付和前端重组，都必须在这些原则内重新制定清晰计划。
