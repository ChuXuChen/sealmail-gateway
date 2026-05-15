package com.sealmail.infra.mail.auth;

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

    private String clean(String value) {
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.replace("\" \"", "");
    }
}
