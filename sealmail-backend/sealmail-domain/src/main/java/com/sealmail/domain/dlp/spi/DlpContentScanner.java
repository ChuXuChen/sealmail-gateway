package com.sealmail.domain.dlp.spi;

import com.sealmail.domain.dlp.DlpScanResult;
import com.sealmail.domain.mailsecurity.MailEnvelope;

/**
 * DLP 内容扫描器 SPI 接口
 * 所有 DLP 扫描器实现都需要实现此接口
 */
public interface DlpContentScanner {

    /**
     * 获取扫描器名称
     */
    String getName();

    /**
     * 获取扫描器描述
     */
    String getDescription();

    /**
     * 扫描邮件内容
     *
     * @param subject 邮件主题
     * @param body 邮件正文
     * @param rawContent 原始邮件内容
     * @return DLP 扫描结果
     */
    DlpScanResult scan(String subject, String body, byte[] rawContent);

    default DlpScanResult scan(String subject, String body, byte[] rawContent, MailEnvelope envelope) {
        return scan(subject, body, rawContent);
    }

    /**
     * 扫描器是否启用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 扫描优先级，数值越小优先级越高
     */
    default int getPriority() {
        return 100;
    }
}
