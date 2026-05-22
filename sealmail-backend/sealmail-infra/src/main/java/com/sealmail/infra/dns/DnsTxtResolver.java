package com.sealmail.infra.dns;

import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Component
public class DnsTxtResolver {

    public List<String> txt(String name) {
        return lookup(name, "TXT");
    }

    public List<String> mx(String name) {
        return lookup(name, "MX");
    }

    public List<String> a(String name) {
        return lookup(name, "A");
    }

    public List<String> aaaa(String name) {
        return lookup(name, "AAAA");
    }

    private List<String> lookup(String name, String type) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(name, new String[]{type});
            Attribute attribute = attributes.get(type);
            if (attribute == null) {
                return List.of();
            }
            List<String> records = new ArrayList<>();
            NamingEnumeration<?> values = attribute.getAll();
            while (values.hasMore()) {
                records.add(clean(String.valueOf(values.next())));
            }
            return records;
        } catch (Exception e) {
            return List.of();
        }
    }

    String clean(String value) {
        String cleaned = value.trim();
        return concatenateQuotedStrings(cleaned);
    }

    private String concatenateQuotedStrings(String value) {
        if (value.isEmpty() || value.charAt(0) != '"') {
            return value;
        }

        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < value.length()) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index >= value.length()) {
                return result.toString();
            }
            if (value.charAt(index) != '"') {
                result.append(value.substring(index).trim());
                return result.toString();
            }

            index++;
            boolean closed = false;
            while (index < value.length()) {
                char current = value.charAt(index++);
                if (current == '\\' && index < value.length()) {
                    result.append(value.charAt(index++));
                    continue;
                }
                if (current == '"') {
                    closed = true;
                    break;
                }
                result.append(current);
            }
            if (!closed) {
                return value;
            }
        }
        return result.toString();
    }
}
