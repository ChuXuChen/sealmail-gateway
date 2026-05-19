package com.sealmail.infra.dlp.config;

import com.sealmail.domain.dlp.DlpRuleType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class DlpConfigValidation {

    private DlpConfigValidation() {
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    static String normalizeDomain(String value) {
        return Optional.ofNullable(value)
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\.+$", "");
    }

    static List<String> normalizeDomains(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(DlpConfigValidation::normalizeDomain)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    static List<String> normalizeTextList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    static List<String> normalizeIds(List<String> values) {
        return normalizeTextList(values);
    }

    static List<String> nullableNormalizedPatternIds(List<String> patternIds) {
        if (patternIds == null) {
            return null;
        }
        return patternIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    static boolean selectsAllPatterns(DlpSelectionUpdate update) {
        if (update.patternMode() == null || update.patternMode().isBlank()) {
            return update.patternIds() == null;
        }
        return "ALL".equalsIgnoreCase(update.patternMode());
    }

    static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String message) {
        try {
            return Enum.valueOf(enumClass, requireText(value, message).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(message);
        }
    }

    static <E extends Enum<E>> E parseEnumOrDefault(Class<E> enumClass, String value, E defaultValue, String message) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return parseEnum(enumClass, value, message);
    }

    static <E extends Enum<E>> E nullableEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
    }

    static <E extends Enum<E>> List<String> parseEnumNames(Class<E> enumClass, List<String> values, String message) {
        return normalizeTextList(values).stream()
                .map(value -> parseEnum(enumClass, value, message).name())
                .toList();
    }

    static DlpRuleType canonicalType(DlpRuleType type) {
        if (type == DlpRuleType.REGEX || type == DlpRuleType.KEYWORD || type == DlpRuleType.BUILTIN) {
            return DlpRuleType.PATTERN;
        }
        return type != null ? type : DlpRuleType.PATTERN;
    }

    static String keywordRegex(String keywords) {
        List<String> values = normalizeTextList(keywords == null ? List.of() : java.util.Arrays.asList(keywords.split("[,\\n]")));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        return values.stream()
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|", "(?:", ")"));
    }

    static String builtinRegex(String builtinCode) {
        String code = requireText(builtinCode, "内置规则编码不能为空").toUpperCase(Locale.ROOT);
        return switch (code) {
            case "CN_ID_CARD" -> "\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b";
            case "BANK_CARD" -> "\\b\\d{16,19}\\b";
            case "API_KEY" -> "(?i)\\b(?:api[_-]?key|secret[_-]?key|access[_-]?token)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_\\-]{16,}";
            case "PRIVATE_KEY" -> "-----BEGIN [A-Z ]*PRIVATE KEY-----";
            case "PHONE_CN" -> "\\b1[3-9]\\d{9}\\b";
            default -> throw new IllegalArgumentException("未知内置规则编码: " + builtinCode);
        };
    }

    static void validateRegex(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则表达式无效: " + e.getMessage());
        }
    }

    static void validateWildcards(List<String> patterns) {
        for (String pattern : normalizeTextList(patterns)) {
            wildcardToRegex(pattern);
        }
    }

    static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == '*') {
                sb.append(".*");
            } else if (".[]{}()+-^$?|\\ ".indexOf(ch) >= 0) {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
