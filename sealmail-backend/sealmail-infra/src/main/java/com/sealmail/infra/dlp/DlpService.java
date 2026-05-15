package com.sealmail.infra.dlp;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.dlp.DlpViolation;
import com.sealmail.domain.mailsecurity.MailEnvelope;
import com.sealmail.domain.dlp.spi.DlpContentScanner;
import com.sealmail.domain.policy.DispositionAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DLP 服务
 * 编排所有 DLP 扫描器，执行内容检测并汇总结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DlpService {

    private final List<DlpContentScanner> scanners;

    /**
     * 执行完整的 DLP 扫描
     *
     * @param subject 邮件主题
     * @param body 邮件正文
     * @param rawContent 原始邮件内容
     * @return 综合扫描结果
     */
    public DlpScanResult scan(String subject, String body, byte[] rawContent) {
        return scan(subject, body, rawContent, null);
    }

    public DlpScanResult scan(String subject, String body, byte[] rawContent, MailEnvelope envelope) {
        long startTime = System.currentTimeMillis();
        List<DlpViolation> allViolations = new ArrayList<>();

        List<DlpContentScanner> orderedScanners = scanners.stream()
                .sorted(Comparator.comparingInt(DlpContentScanner::getPriority)
                        .thenComparing(DlpContentScanner::getName))
                .toList();

        for (DlpContentScanner scanner : orderedScanners) {
            if (!scanner.isEnabled()) {
                continue;
            }

            try {
                DlpScanResult result = scanner.scan(subject, body, rawContent, envelope);
                if (result.hasViolations()) {
                    allViolations.addAll(result.getViolations());
                    log.debug("Scanner {} found {} violations", scanner.getName(), result.getViolations().size());
                }
            } catch (Exception e) {
                log.error("Scanner {} failed: {}", scanner.getName(), e.getMessage(), e);
                allViolations.add(new DlpViolation(
                        "SCAN_ERROR",
                        "DLP scanner failed: " + scanner.getName(),
                        truncate(e.getMessage()),
                        10,
                        DispositionAction.BLOCK
                ));
            }
        }

        allViolations.sort(Comparator.comparingInt((DlpViolation violation) ->
                        DlpScanResult.actionPriority(violation.getAction())).reversed()
                .thenComparing(Comparator.comparingInt(DlpViolation::getSeverity).reversed())
                .thenComparing(DlpViolation::getRuleName));

        long duration = System.currentTimeMillis() - startTime;

        log.info("DLP scan completed in {}ms, found {} violations",
                duration, allViolations.size());

        return new DlpScanResult("CompositeDlpScanner", allViolations, duration);
    }

    /**
     * 快速检查是否有需要拦截的内容
     */
    public boolean shouldBlock(String subject, String body, byte[] rawContent) {
        DlpScanResult result = scan(subject, body, rawContent);
        return result.shouldBlock() || result.shouldQuarantine();
    }

    /**
     * 检查是否需要强制加密
     */
    public boolean requiresEncryption(String subject, String body, byte[] rawContent) {
        DlpScanResult result = scan(subject, body, rawContent);
        return result.shouldEncrypt();
    }

    /**
     * 获取最高级别的处理动作
     */
    public DispositionAction getDispositionAction(String subject, String body, byte[] rawContent) {
        return scan(subject, body, rawContent).getFinalAction();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
