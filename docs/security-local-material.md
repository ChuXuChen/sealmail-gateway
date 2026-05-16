# 本地安全材料说明

仓库不得提交真实私钥、证书、keystore、数据库密码、SMTP 密码、JWT secret 或生产账号凭据。

本地开发或部署时，应通过以下方式注入敏感信息：

- 环境变量。
- 外部挂载文件路径。
- secret manager 引用。
- 仅存在于本机的 `.env` 文件。

常用环境变量示例：

```text
SEALMAIL_DB_URL=jdbc:postgresql://localhost:5432/sealmail
SEALMAIL_DB_USERNAME=sealmail
SEALMAIL_DB_PASSWORD=...
SEALMAIL_JWT_SECRET_REF=env:SEALMAIL_JWT_SECRET
SEALMAIL_KEYSTORE_PATH=/run/secrets/sealmail-keystore.p12
SEALMAIL_SMTP_KEYSTORE_PASSWORD_SECRET_REF=env:SEALMAIL_KEYSTORE_PASSWORD
```

证书、私钥和 keystore 应放在仓库外，并通过配置路径引用。

Relay 策略属于运行时业务配置，应通过管理 API 写入数据库；密码只保存 secret 引用，例如 `file:/run/secrets/sealmail_relay_password`。

提交前可运行敏感材料扫描：

```bash
ops/scan-sensitive-material.sh
```

该脚本会检查私钥 PEM、证书 PEM 和明显的明文 secret/password/token 赋值。若确需新增本地样例，只能使用环境变量占位或 secret 引用，不要写入真实材料。
