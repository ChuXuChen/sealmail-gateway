package com.sealmail.sealmailbase.util;

import java.util.Collection;
import java.util.Objects;

/**
 * 通用字符串工具类，所有方法均为 null-safe。
 * 设计为不可实例化，通过静态方法提供服务。
 *
 * @author sealmail
 * @since 1.0.0
 */
public final class StringUtils {

    private StringUtils() {
        // 工具类禁止实例化
    }

    // ==================== 空值判断 ====================

    /**
     * 判断字符串是否为空（null 或 长度为 0）。
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否为空白（null 或 只包含空白字符）。
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 判断字符串不为空。
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串不为空白。
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 如果字符串为 null，则返回默认值。
     */
    public static String defaultIfNull(String str, String defaultStr) {
        return str == null ? defaultStr : str;
    }

    /**
     * 如果字符串为空白，则返回默认值。
     */
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }

    // ==================== 去除空白 ====================

    /**
     * 去除字符串首尾空白，null 安全（null 返回 null）。
     */
    public static String trim(String str) {
        return str == null ? null : str.strip();
    }

    /**
     * 去除首尾空白，若结果为空白字符串则返回 null。
     */
    public static String trimToNull(String str) {
        String trimmed = trim(str);
        return isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 去除首尾空白，若为 null 则返回空字符串。
     */
    public static String trimToEmpty(String str) {
        return str == null ? "" : str.strip();
    }

    // ==================== 截取与连接 ====================

    /**
     * 安全截取字符串，超出长度不报错。
     */
    public static String substring(String str, int beginIndex, int endIndex) {
        if (str == null) {
            return null;
        }
        int len = str.length();
        if (beginIndex < 0) {
            beginIndex = 0;
        }
        if (endIndex > len) {
            endIndex = len;
        }
        if (beginIndex >= endIndex) {
            return "";
        }
        return str.substring(beginIndex, endIndex);
    }

    /**
     * 将多个对象用指定分隔符连接，忽略 null 元素。
     * 常用于邮件头拼接等场景。
     */
    public static String joinWithDelimiter(String delimiter, Object... elements) {
        if (elements == null || elements.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object element : elements) {
            if (element != null) {
                if (!sb.isEmpty()) {
                    sb.append(delimiter);
                }
                sb.append(element);
            }
        }
        return sb.toString();
    }

    /**
     * 将集合元素用分隔符拼接，忽略 null。
     */
    public static String join(Collection<?> collection, String delimiter) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : collection) {
            if (obj != null) {
                if (!sb.isEmpty()) {
                    sb.append(delimiter);
                }
                sb.append(obj);
            }
        }
        return sb.toString();
    }

    // ==================== 大小写与转换 ====================

    /**
     * null 安全的转换为小写。
     */
    public static String lowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }

    /**
     * null 安全的转换为大写。
     */
    public static String upperCase(String str) {
        return str == null ? null : str.toUpperCase();
    }

    // ==================== 邮件场景常用 ====================

    /**
     * 从 SMTP 信封或邮件头中提取纯邮箱地址。
     * 例如："John Doe <john@example.com>" -> "john@example.com"
     */
    public static String extractEmailAddress(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        int start = trimmed.indexOf('<');
        int end = trimmed.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return trimmed.substring(start + 1, end).strip();
        }
        // 没有尖括号，直接返回修剪后的字符串
        return trimmed;
    }

    /**
     * 检查字符串是否看起来像一个合法的邮箱地址（简单校验）。
     */
    public static boolean looksLikeEmail(String str) {
        if (isBlank(str)) {
            return false;
        }
        String trimmed = str.strip();
        int atIndex = trimmed.indexOf('@');
        int lastDot = trimmed.lastIndexOf('.');
        return atIndex > 0 && lastDot > atIndex + 1 && lastDot < trimmed.length() - 1;
    }

    /**
     * 比较两个字符串是否相等，null 安全。
     */
    public static boolean equals(String str1, String str2) {
        return Objects.equals(str1, str2);
    }

    /**
     * 比较两个字符串是否相等忽略大小写，null 安全。
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }
}