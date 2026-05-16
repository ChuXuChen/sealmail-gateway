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
SEALMAIL_JWT_SECRET=...
SEALMAIL_RELAY_HOST=smtp.example.com
SEALMAIL_RELAY_PORT=587
SEALMAIL_RELAY_USERNAME=...
SEALMAIL_RELAY_PASSWORD=...
SEALMAIL_KEYSTORE_PATH=/run/secrets/sealmail-keystore.p12
SEALMAIL_KEYSTORE_PASSWORD=...
```

证书、私钥和 keystore 应放在仓库外，并通过配置路径引用。
