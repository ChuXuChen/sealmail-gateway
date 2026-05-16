package com.sealmail.app.mapper;

import com.sealmail.app.dto.response.AuditLogResponse;
import com.sealmail.domain.audit.AuditLog;
import org.springframework.stereotype.Component;

/**
 * 审计日志对象映射器
 */
@Component
public class AuditDtoMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .type(log.getType().name())
                .typeDisplayName(getTypeDisplayName(log.getType().name()))
                .userId(log.getUserId())
                .username(log.getUsername())
                .ipAddress(log.getIpAddress())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .action(log.getAction())
                .detail(log.getDetail())
                .success(log.isSuccess())
                .errorMessage(log.getErrorMessage())
                .occurredAt(log.getOccurredAt())
                .build();
    }

    private String getTypeDisplayName(String type) {
        return switch (type) {
            case "USER_LOGIN" -> "用户登录";
            case "USER_LOGOUT" -> "用户登出";
            case "USER_LOGIN_FAILED" -> "登录失败";
            case "USER_CREATED" -> "创建用户";
            case "USER_UPDATED" -> "更新用户";
            case "USER_DELETED" -> "删除用户";
            case "USER_PASSWORD_CHANGED" -> "修改密码";
            case "USER_UNLOCKED" -> "解锁用户";
            case "USER_ROLE_CHANGED" -> "修改角色";
            case "CERTIFICATE_ISSUED" -> "签发证书";
            case "CERTIFICATE_IMPORTED" -> "导入证书";
            case "CERTIFICATE_TRUSTED" -> "信任证书";
            case "CERTIFICATE_UNTRUSTED" -> "撤销信任";
            case "CERTIFICATE_REVOKED" -> "吊销证书";
            case "CERTIFICATE_DELETED" -> "删除证书";
            case "EMAIL_RECEIVED" -> "接收邮件";
            case "EMAIL_ROUTED" -> "路由决策";
            case "EMAIL_CERTIFICATE_SELECTED" -> "证书选择";
            case "EMAIL_DELIVERED" -> "投递邮件";
            case "EMAIL_RELAYED" -> "Relay 投递";
            case "EMAIL_RELEASED" -> "放行邮件";
            case "EMAIL_REJECTED" -> "拒收邮件";
            case "EMAIL_QUARANTINED" -> "DLP 隔离";
            case "EMAIL_ENCRYPTED" -> "加密邮件";
            case "EMAIL_DECRYPTED" -> "解密邮件";
            case "EMAIL_SIGNED" -> "签名邮件";
            case "EMAIL_VERIFIED" -> "验签邮件";
            case "DLP_VIOLATION" -> "DLP 命中";
            case "SYSTEM_CONFIG_CHANGED" -> "修改配置";
            case "SYSTEM_STARTUP" -> "系统启动";
            case "SYSTEM_SHUTDOWN" -> "系统关闭";
            default -> "其他操作";
        };
    }
}
