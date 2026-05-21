package com.sun.mail.util;

import java.util.Properties;
import javax.mail.Session;

/**
 * Minimal JavaMail compatibility shim for SubEthaSMTP 3.x.
 *
 * <p>SubEthaSMTP validates SMTP envelope addresses through
 * {@code javax.mail.internet.InternetAddress}. The API-only JavaMail jar keeps
 * that class but omits this implementation helper. Providing only the property
 * reader avoids reintroducing the old JavaMail runtime mailcap handlers, which
 * can interfere with the Jakarta Mail stack used by S/MIME processing.
 */
public final class PropUtil {

    private PropUtil() {
    }

    public static int getIntProperty(Properties props, String name, int def) {
        return getInt(getProp(props, name), def);
    }

    public static boolean getBooleanProperty(Properties props, String name, boolean def) {
        return getBoolean(getProp(props, name), def);
    }

    @Deprecated
    public static int getIntSessionProperty(Session session, String name, int def) {
        return getIntProperty(session.getProperties(), name, def);
    }

    @Deprecated
    public static boolean getBooleanSessionProperty(Session session, String name, boolean def) {
        return getBooleanProperty(session.getProperties(), name, def);
    }

    public static boolean getBooleanSystemProperty(String name, boolean def) {
        try {
            return getBoolean(getProp(System.getProperties(), name), def);
        } catch (SecurityException ignored) {
            try {
                String value = System.getProperty(name);
                if (value == null) {
                    return def;
                }
                return def ? !value.equalsIgnoreCase("false") : value.equalsIgnoreCase("true");
            } catch (SecurityException nestedIgnored) {
                return def;
            }
        }
    }

    private static Object getProp(Properties props, String name) {
        Object value = props.get(name);
        return value != null ? value : props.getProperty(name);
    }

    private static int getInt(Object value, int def) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static boolean getBoolean(Object value, boolean def) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return def ? !string.equalsIgnoreCase("false") : string.equalsIgnoreCase("true");
        }
        return def;
    }
}
