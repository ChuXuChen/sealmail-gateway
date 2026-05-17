# SealMail Gateway 企业基础功能扩展计划

## 1. 计划目标

本文档定义 SealMail Gateway 在史诗大重构收尾后的下一组功能扩展路线。目标级别限定为“企业基础可交付版”，即功能应能支撑企业内网或单租户部署的真实试用和初步交付，但不追求完整商用套件的全部高级能力。

本计划必须与以下文档同时使用：

- `docs/refactoring-principles.md`
- `docs/frontend-revision-plan.md`
- `docs/security-local-material.md`

本计划覆盖的新增或增强能力：

- DKIM/SPF/DMARC 完整化。
- DLP 企业基础扩充。
- MTA/SMTP 设置产品化。
- PDF 加密策略。
- Docker 多实例租户隔离交付。

总体工程量预估：

```text
企业基础可交付版增量：17,000 - 33,000 行
建议预算中位数：约 25,000 行
```

该估算包含后端、前端、测试、Flyway migration、部署模板和文档，不包含第三方依赖源码。

## 2. 当前系统基线

当前系统已经具备以下基础：

- 后端已经拆分为 `domain`、`app`、`infra`、`web`、`boot`。
- 邮件主链路已经迁移到 Spring Integration flow。
- DLP 已有规则、选择范围、正则扫描、隔离队列和释放工作流。
- 邮件认证已有 DKIM/SPF/DMARC 配置、简化 verifier、出站 DKIM 签名 step 和前端配置入口。
- SMTP/MTA 已有 SubEtha SMTP 入口、RelayPolicy、SMTP relay client、SMTP 探测和设置页展示。
- 审计、隔离、运行时策略和 secret 引用已经具备初步治理基础。
- Docker 多实例租户隔离尚未作为交付形态固化。
- PDF 加密尚未形成独立子系统。

当前定位：

```text
技术底座：企业基础可交付版
产品体验：能跑通增强版
商业完整度：商用版前夜
```

本计划的目标是把关键企业能力补到同一交付水准，而不是重新进行架构大重构。

## 3. 总体执行规则

- 每个阶段只处理一类能力，不混入无关重构。
- 不修改已发布 Flyway migration，只能新增 migration。
- 不提交真实私钥、证书、keystore、数据库密码、SMTP 密码、JWT secret、DKIM 私钥或生产凭据。
- 不引入绕过 `domain/app/infra/web/boot` 边界的跨层依赖。
- 新功能必须接入统一审计、错误分类、权限检查和测试门禁。
- 前端遵守 `docs/frontend-revision-plan.md` 的 Ant Design 默认风格约束。
- 生产能力优先使用成熟库；只有业务编排、策略模型、审计和 UI 自行实现。
- 每个阶段必须有最小可验收命令。
- 每个阶段完成后更新本计划的实际偏差和未完成项。

建议通用验收命令：

```bash
cd sealmail-backend
mvn test

cd ../sealmail-frontend
npm run lint
npm run build
```

## 4. 非目标

以下内容不属于本计划的直接目标：

- 多租户共享数据库的行级租户隔离。
- 跨租户集中控制台。
- 云厂商 marketplace 发布。
- AI/ML 自适应 DLP。
- 完整邮件归档系统。
- 大规模 HA 邮件队列集群。
- 完整 MTA 替代品。
- Office 文档 DRM。
- 移动端管理 App。

租户隔离采用单租户单实例策略，通过 Docker 多实例、独立数据库、独立 volume、独立 secret 和独立域名入口完成。

## 5. 阶段路线

推荐顺序：

```text
阶段 0：扩展基线与验收样例
阶段 1：Docker 多实例交付基线
阶段 2：MTA/SMTP 设置产品化
阶段 3：DKIM/SPF/DMARC 完整化
阶段 4：DLP 企业基础扩充
阶段 5：PDF 加密策略
阶段 6：统一运营台收口
阶段 7：企业交付硬化
```

阶段 1 应优先于其他能力落地。原因是 Docker 多实例会定义 secret、volume、域名入口、初始化账号和备份方式，后续 DKIM 私钥、DLP 附件扫描、PDF 加密材料和 SMTP 密码都应服从该交付边界。

## 阶段 0：扩展基线与验收样例

目标：为新增功能建立可执行边界，避免再次变成无边界大重构。

范围：

- 记录当前邮件认证、DLP、MTA、设置页和部署能力现状。
- 为新增能力建立验收样例目录和测试命名规范。
- 明确第三方库选型原则。
- 明确不做共享数据库多租户。

交付物：

- 本文档。
- 新增或更新少量 architecture/hardening test。
- 建立测试样例目录命名：
  - `mail/auth`
  - `dlp`
  - `pdf`
  - `deployment`

验收标准：

- 文档存在且能指导后续阶段。
- 不引入业务行为变更。
- 不改历史 migration。

建议验收命令：

```bash
git diff -- docs/enterprise-feature-expansion-plan.md
```

工程量预估：200 - 500 行。

## 阶段 1：Docker 多实例交付基线

目标：形成单租户单实例交付方式，为企业试用和私有化部署提供清晰边界。

范围：

- Docker Compose 模板。
- `.env.example`。
- 每实例独立 PostgreSQL。
- 每实例独立 secret 引用。
- 每实例独立上传/隔离/日志 volume。
- 初始化管理员账号。
- 实例名称与组织名称展示。
- 健康检查。
- 备份与恢复说明。

实施步骤：

1. 新增部署模板：
   - `deploy/docker-compose.single-tenant.yml`
   - `deploy/env.single-tenant.example`
   - `deploy/README.md`
2. 为后端增加只读实例元数据：
   - `instanceId`
   - `organizationName`
   - `deploymentMode`
3. 前端设置页或顶部区域展示当前实例名称。
4. 后端健康检查返回实例标识和版本信息。
5. 明确 volume：
   - database
   - uploaded mail material
   - quarantine raw content
   - DKIM/PDF secret mount
   - logs
6. 编写备份/恢复脚本或文档样例。
7. 补充敏感信息扫描说明。

验收标准：

- 单实例可通过 Docker Compose 启动。
- 两套不同 `.env` 可以并行运行，端口、数据库、secret 和 volume 不冲突。
- 前端能显示当前组织或实例名称。
- 不需要应用层 `tenant_id`。
- 文档明确生产 secret 不进入 Git。

建议验收命令：

```bash
cd sealmail-backend
mvn test

cd ../sealmail-frontend
npm run lint
npm run build
```

工程量预估：

```text
应用代码：800 - 2,000 行
部署模板/脚本/文档：500 - 2,000 行
```

## 阶段 2：MTA/SMTP 设置产品化

目标：把已有 SMTP 入口、relay 策略和探测能力从“能跑通”提升到“企业基础可交付”。

范围：

- SMTP 入口展示。
- Relay 策略管理。
- Postfix/content-filter 对接说明。
- SMTP 探测。
- 投递失败诊断。
- 队列与失败事件的最小可观测性。

实施步骤：

1. 梳理当前 SMTP 入口配置和 relay 策略边界。
2. 新增 MTA 设置页面或重组 `Settings` 邮件区域：
   - SMTP 入口。
   - Relay 策略。
   - Postfix/content-filter 示例。
   - 探测工具。
3. 增强 SMTP 探测结果：
   - DNS 可达。
   - TCP 可达。
   - EHLO 响应。
   - AUTH 支持。
   - MAIL FROM/RCPT TO 预检。
4. Relay 失败分类：
   - 连接失败。
   - AUTH 失败。
   - 4xx 临时失败。
   - 5xx 永久失败。
5. 失败写入审计和处理轨迹。
6. 增加配置保存前校验和危险变更确认。
7. 补充文档：
   - Postfix content-filter。
   - 网关前置/后置部署拓扑。
   - SPF 真实来源 IP 注意事项。

验收标准：

- 管理员能在页面上完成 relay 配置和探测。
- 探测失败能说明失败阶段。
- relay 失败能进入统一错误流并可审计。
- 密码只保存 secret 引用或配置状态，不回显明文。
- Postfix 对接有可复制示例。

建议测试：

- `SmtpRelayClientTest`
- `RelayStepTest`
- `SealMailSmtpServerTest`
- `RuntimePolicyController` 或对应 web 层测试。
- 前端 build/lint。

工程量预估：2,500 - 5,000 行。

## 阶段 3：DKIM/SPF/DMARC 完整化

目标：将现有邮件认证从简化实现提升到企业基础可交付能力。

范围：

- DKIM 出站签名。
- DKIM 入站验签。
- SPF DNS 机制。
- DMARC 对齐和策略处理。
- Authentication-Results。
- DNS 记录生成与健康检查。
- 邮件认证审计。

实施步骤：

1. 明确第三方库选型。优先使用成熟 DNS/Mail Auth 相关库，不手写完整协议细节。
2. DKIM 增强：
   - selector/domain 配置。
   - relaxed/relaxed canonicalization。
   - 多 DKIM header 验证。
   - DNS 公钥获取和错误分类。
   - 出站签名失败可审计。
3. SPF 增强：
   - `ip4`/`ip6` CIDR。
   - `a`、`mx`、`include`、`redirect`、`exists`。
   - DNS lookup 限制。
   - temperror/permerror 分类。
   - 私网中继跳过策略。
4. DMARC 增强：
   - `adkim`、`aspf`。
   - relaxed/strict alignment。
   - `p`、`sp`、`pct`。
   - none/quarantine/reject 策略映射。
   - 本地失败动作：仅记录或按策略隔离。
5. Authentication-Results：
   - 统一 authserv-id。
   - 写入或更新邮件头。
   - 避免重复或伪造内部结果。
6. 健康检查：
   - DKIM TXT。
   - SPF TXT。
   - DMARC TXT。
   - DNS 记录可复制。
   - 最近检测结果。
7. 前端重组：
   - 从域名配置大弹窗拆为邮件认证配置页或 Tabs。
   - 显示 DKIM/SPF/DMARC 状态卡。
   - 提供 DNS TXT 复制和诊断。
8. 测试：
   - 协议单元测试。
   - DNS resolver fake 测试。
   - pipeline step 测试。
   - 隔离策略测试。

验收标准：

- 出站邮件在配置有效时带 DKIM-Signature。
- 入站 SPF/DKIM/DMARC 结果进入 `Authentication-Results`。
- DMARC quarantine/reject 可按配置进入隔离。
- DNS 记录页面能给出可复制 TXT。
- DKIM 私钥只通过 path/secret ref 读取，不进入数据库明文字段。
- 协议失败路径有明确错误分类和审计记录。

建议验收命令：

```bash
cd sealmail-backend
mvn -pl sealmail-infra -am test
mvn -pl sealmail-web -am test

cd ../sealmail-frontend
npm run lint
npm run build
```

工程量预估：3,500 - 6,000 行。

## 阶段 4：DLP 企业基础扩充

目标：将当前 DLP 从规则正则扫描提升为企业基础策略引擎和安全事件运营能力。

范围：

- 内容抽取。
- 附件扫描。
- 规则库。
- 策略集。
- 命中证据。
- 事件详情。
- 测试规则工具。
- 隔离工作台增强。

实施步骤：

1. 内容抽取：
   - text/plain。
   - HTML 正文转文本。
   - 常见附件文本抽取。
   - PDF 文本抽取。
   - 附件大小限制。
   - 解压深度限制。
2. 检测引擎：
   - 正则规则。
   - 关键字/字典规则。
   - 组合条件。
   - 阈值。
   - 严重级别。
   - 动作：warn、must encrypt、quarantine、block。
3. 策略模型：
   - 全局。
   - 发件域。
   - 收件域。
   - 方向。
   - 附件类型。
   - 规则组。
4. 命中证据：
   - 脱敏片段。
   - 命中位置。
   - 规则名称。
   - 附件名。
   - 风险等级。
5. 隔离工作台：
   - 风险筛选。
   - 命中原因。
   - 证据详情。
   - 批量释放/拒绝。
   - 时间线。
6. 前端：
   - 规则库。
   - 策略集。
   - 测试规则。
   - 命中事件详情。
7. 测试：
   - 扫描器测试。
   - MIME/附件提取测试。
   - 策略选择测试。
   - pipeline 集成测试。
   - 隔离 API 测试。

验收标准：

- 能对正文、HTML 和至少一种附件文本来源做 DLP 检测。
- 能配置规则组和策略作用域。
- 命中事件包含脱敏证据，不泄露完整敏感正文。
- DLP 命中能稳定进入隔离或阻断。
- 管理员能在 UI 中测试规则并查看命中解释。
- 大附件和异常附件不会拖垮邮件主链路。

建议验收命令：

```bash
cd sealmail-backend
mvn -pl sealmail-domain -am test
mvn -pl sealmail-infra -am test
mvn -pl sealmail-web -am test

cd ../sealmail-frontend
npm run lint
npm run build
```

工程量预估：7,000 - 12,000 行。

## 阶段 5：PDF 加密策略

目标：为企业外发敏感文档提供基础 PDF 加密能力，并与 DLP、隔离释放和审计联动。

范围：

- PDF 附件识别。
- PDF 加密。
- 密码策略。
- 收件人密码传递策略。
- DLP 动作联动。
- 审计和失败处理。

实施步骤：

1. 引入 PDF 处理库。优先选择许可证清晰、适合服务端加密的成熟库。
2. 定义 PDF 加密端口：
   - `PdfEncryptionPort`
   - `PdfEncryptionPolicy`
   - `PdfEncryptionResult`
3. 定义策略：
   - 对全部 PDF 加密。
   - 仅 DLP 命中时加密。
   - 指定域名/方向启用。
   - 加密失败时隔离或阻断。
4. 密码策略：
   - 随机密码。
   - 管理员设置模板。
   - 按收件人生成。
   - 密码传递方式先支持审计记录/管理员复制，不默认明文同信发送。
5. 邮件改写：
   - 替换 PDF 附件。
   - 保留文件名。
   - 标记加密结果。
6. pipeline 集成：
   - 出站 DLP 后、签名/加密前处理。
   - 隔离释放时可选择加密 PDF 后放行。
7. 前端：
   - PDF 加密设置。
   - 隔离释放时加密选项。
   - 加密结果展示。
8. 测试：
   - PDF 加密可打开性测试。
   - 非 PDF 附件跳过。
   - 失败进入错误流。
   - 大文件限制。

验收标准：

- 出站 PDF 可按策略加密。
- 加密后的 PDF 附件能用密码打开。
- 密码不写入普通日志、审计详情或邮件正文。
- 加密失败能进入统一错误流或隔离。
- DLP 命中场景可触发 PDF 加密后释放。

建议验收命令：

```bash
cd sealmail-backend
mvn -pl sealmail-domain -am test
mvn -pl sealmail-infra -am test
mvn -pl sealmail-web -am test

cd ../sealmail-frontend
npm run lint
npm run build
```

工程量预估：3,000 - 6,000 行。

## 阶段 6：统一运营台收口

目标：将新增功能呈现为统一的企业邮件安全网关运营台，而不是分散配置页。

范围：

- Dashboard。
- 隔离中心。
- DLP。
- 邮件认证。
- MTA 设置。
- PDF 加密。
- 审计日志。

实施步骤：

1. Dashboard 增加安全态势：
   - 今日处理邮件。
   - DLP 命中。
   - 隔离待处理。
   - DKIM/SPF/DMARC 健康状态。
   - Relay 健康状态。
2. 隔离中心增强：
   - 风险等级。
   - 命中证据。
   - 处理时间线。
   - 放行/拒绝/加密放行。
3. 邮件认证页重组：
   - 状态卡。
   - DNS 记录。
   - 健康检查。
   - 配置表单。
4. 设置页重组：
   - MTA/SMTP。
   - Relay。
   - PDF 加密。
   - Secret 状态。
5. 审计日志支持按 processing id 串联事件。

验收标准：

- 管理员能从首页发现关键风险。
- 常见处置不需要跨多个页面来回查找。
- 所有新增配置都有明确保存反馈和错误提示。
- UI 遵守 Ant Design 默认风格，不引入额外视觉系统。

建议验收命令：

```bash
cd sealmail-frontend
npm run lint
npm run build
```

工程量预估：2,000 - 4,000 行。该工程量已部分包含在前述各功能前端估算中，若各阶段已完成页面重组，本阶段只做整合和一致性修正。

## 阶段 7：企业交付硬化

目标：完成企业基础可交付版收口。

范围：

- 全量测试。
- 架构门禁。
- 敏感信息扫描。
- 部署文档。
- 故障排查文档。
- 备份恢复演练。

实施步骤：

1. 增加 hardening 规则：
   - 禁止明文 DKIM 私钥。
   - 禁止 PDF 密码进入日志。
   - 禁止 DLP 命中证据保存完整敏感正文。
   - 禁止新增 `tenant_id` 半成品模型。
2. 敏感信息扫描：
   - PEM 块。
   - 常见 secret key。
   - SMTP 密码。
   - DKIM 私钥。
3. 编写部署文档：
   - 单租户 Docker。
   - Postfix 对接。
   - DNS 配置。
   - 备份恢复。
4. 编写运维排障：
   - DKIM 失败。
   - SPF 误判。
   - DMARC 隔离。
   - relay 失败。
   - DLP 误报。
   - PDF 加密失败。
5. 运行全量后端、前端验收命令。

验收标准：

- 后端全量测试通过。
- 前端 lint/build 通过。
- 敏感信息扫描无真实密钥材料。
- Docker 单租户部署文档可被执行。
- 关键故障场景有排障说明。

建议验收命令：

```bash
cd sealmail-backend
mvn test

cd ../sealmail-frontend
npm run lint
npm run build
```

工程量预估：1,000 - 2,500 行。

## 6. 工程量汇总

按企业基础可交付版估算：

| 功能 | 增量行数 |
|---|---:|
| Docker 多实例租户隔离 | 800 - 2,000 |
| 部署模板/脚本/文档 | 500 - 2,000 |
| MTA/SMTP 设置产品化 | 2,500 - 5,000 |
| DKIM/SPF/DMARC 完整化 | 3,500 - 6,000 |
| DLP 企业基础扩充 | 7,000 - 12,000 |
| PDF 加密策略 | 3,000 - 6,000 |
| 企业交付硬化 | 1,000 - 2,500 |
| **合计** | **18,300 - 35,500** |

其中部分前端整合和测试会在阶段间复用，实际落地目标建议控制在：

```text
17,000 - 33,000 行
```

如果范围压缩为演示版：

```text
12,000 - 18,000 行
```

如果继续推进到更完整商用版：

```text
30,000 - 45,000 行
```

## 7. 风险与控制

### 7.1 DKIM/SPF/DMARC 风险

风险：

- 手写协议细节容易产生兼容性漏洞。
- DNS 超时会拖慢邮件主链路。
- 内网中继会导致 SPF 来源 IP 误判。

控制：

- 优先使用成熟库。
- DNS 查询必须有超时和缓存。
- 支持跳过私网 relay。
- 所有结果进入审计和 Authentication-Results。

### 7.2 DLP 风险

风险：

- 误报影响业务投递。
- 大附件扫描拖慢主链路。
- 命中证据可能泄露敏感内容。

控制：

- 限制附件大小和扫描时间。
- 命中证据必须脱敏。
- 支持 warn/log-only 模式。
- 提供规则测试工具。

### 7.3 PDF 加密风险

风险：

- 加密后文件不可打开。
- 密码传递不安全。
- 破坏 S/MIME 签名顺序。

控制：

- PDF 加密必须在签名/加密前完成。
- 密码不默认同信发送。
- 提供加密结果测试。
- 失败进入统一错误流。

### 7.4 Docker 多实例风险

风险：

- 多实例端口、volume、secret 混用。
- 备份恢复流程不清晰。

控制：

- 每实例独立 `.env`。
- 文档明确命名约定。
- 健康检查暴露实例 ID。
- 提供备份恢复样例。

## 8. 交付完成定义

达到企业基础可交付版时，应满足：

- 一套 Docker 单租户实例可独立部署。
- 管理员能配置 SMTP/MTA、Relay、DKIM/SPF/DMARC、DLP、PDF 加密策略。
- 邮件主链路能稳定执行认证、DLP、加密、签名、relay 或隔离。
- 所有安全相关动作有审计。
- 失败路径进入统一错误流或隔离队列。
- 前端能支撑日常运营、排障和隔离处置。
- 后端全量测试和前端 build/lint 通过。
- 文档足以支撑企业试用部署和常见故障排查。
