package com.sealmail.app.usecase.audit;

import com.sealmail.app.dto.common.PageRequest;
import com.sealmail.app.dto.common.PageResponse;
import com.sealmail.app.dto.response.AuditLogResponse;
import com.sealmail.app.exception.BusinessException;
import com.sealmail.app.mapper.AuditDtoMapper;
import com.sealmail.app.security.UserContext;
import com.sealmail.domain.audit.AuditLog;
import com.sealmail.domain.audit.AuditLogRepository;
import com.sealmail.domain.audit.AuditLogType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 查询审计日志用例
 */
@Service
@RequiredArgsConstructor
public class QueryAuditLogUseCase {

    private final AuditLogRepository repository;
    private final AuditDtoMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAll(PageRequest pageRequest, UserContext currentUser) {
        if (!currentUser.isAdmin() && !currentUser.isAuditor()) {
            throw BusinessException.forbidden("只有管理员或审计员可以查看审计日志");
        }

        List<AuditLog> logs = repository.findAll(pageRequest.getPage(), pageRequest.getSize());
        long total = repository.count();

        List<AuditLogResponse> items = logs.stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.of(items, total, pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String category, String type, Boolean success,
                                                 PageRequest pageRequest, UserContext currentUser) {
        if (!currentUser.isAdmin() && !currentUser.isAuditor()) {
            throw BusinessException.forbidden("只有管理员或审计员可以查看审计日志");
        }

        List<AuditLogType> types = resolveTypes(category, type);
        List<AuditLog> logs = repository.search(types, success, pageRequest.getPage(), pageRequest.getSize());
        long total = repository.countSearch(types, success);

        List<AuditLogResponse> items = logs.stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.of(items, total, pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> findByType(String type, PageRequest pageRequest, UserContext currentUser) {
        if (!currentUser.isAdmin() && !currentUser.isAuditor()) {
            throw BusinessException.forbidden("只有管理员或审计员可以查看审计日志");
        }

        AuditLogType logType = AuditLogType.valueOf(type);
        List<AuditLog> logs = repository.findByType(logType, pageRequest.getPage(), pageRequest.getSize());
        long total = repository.countByType(logType);

        List<AuditLogResponse> items = logs.stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.of(items, total, pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> findByUserId(String userId, PageRequest pageRequest, UserContext currentUser) {
        if (!currentUser.isAdmin() && !currentUser.isAuditor()) {
            if (!currentUser.getUserId().equals(userId)) {
                throw BusinessException.forbidden("只能查看自己的操作日志");
            }
        }

        List<AuditLog> logs = repository.findByUserId(userId, pageRequest.getPage(), pageRequest.getSize());
        long total = repository.countByUserId(userId);

        List<AuditLogResponse> items = logs.stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.of(items, total, pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> findByTimeRange(Instant startTime, Instant endTime,
                                                          PageRequest pageRequest, UserContext currentUser) {
        if (!currentUser.isAdmin() && !currentUser.isAuditor()) {
            throw BusinessException.forbidden("只有管理员或审计员可以查看审计日志");
        }

        List<AuditLog> logs = repository.findByTimeRange(startTime, endTime, pageRequest.getPage(), pageRequest.getSize());
        long total = repository.countByTimeRange(startTime, endTime);

        List<AuditLogResponse> items = logs.stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.of(items, total, pageRequest);
    }

    private List<AuditLogType> resolveTypes(String category, String type) {
        if (type != null && !type.isBlank()) {
            return List.of(AuditLogType.valueOf(type));
        }
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) {
            return List.of();
        }
        return switch (category.toUpperCase()) {
            case "AUTH" -> List.of(
                    AuditLogType.USER_LOGIN,
                    AuditLogType.USER_LOGIN_FAILED,
                    AuditLogType.USER_LOGOUT,
                    AuditLogType.USER_PASSWORD_CHANGED
            );
            case "USER" -> List.of(
                    AuditLogType.USER_CREATED,
                    AuditLogType.USER_UPDATED,
                    AuditLogType.USER_DELETED,
                    AuditLogType.USER_UNLOCKED,
                    AuditLogType.USER_ROLE_CHANGED
            );
            case "CERTIFICATE" -> List.of(
                    AuditLogType.CERTIFICATE_ISSUED,
                    AuditLogType.CERTIFICATE_IMPORTED,
                    AuditLogType.CERTIFICATE_TRUSTED,
                    AuditLogType.CERTIFICATE_UNTRUSTED,
                    AuditLogType.CERTIFICATE_REVOKED,
                    AuditLogType.CERTIFICATE_DELETED
            );
            case "EMAIL" -> List.of(
                    AuditLogType.EMAIL_RECEIVED,
                    AuditLogType.EMAIL_DELIVERED,
                    AuditLogType.EMAIL_RELEASED,
                    AuditLogType.EMAIL_REJECTED,
                    AuditLogType.EMAIL_QUARANTINED,
                    AuditLogType.EMAIL_ENCRYPTED,
                    AuditLogType.EMAIL_DECRYPTED,
                    AuditLogType.EMAIL_SIGNED,
                    AuditLogType.EMAIL_VERIFIED,
                    AuditLogType.DLP_VIOLATION
            );
            case "SYSTEM" -> List.of(
                    AuditLogType.SYSTEM_CONFIG_CHANGED,
                    AuditLogType.SYSTEM_STARTUP,
                    AuditLogType.SYSTEM_SHUTDOWN,
                    AuditLogType.OTHER
            );
            default -> throw BusinessException.badRequest("未知审计事件分类: " + category);
        };
    }
}
