package com.flipkart.drift.commons.utils;

import com.flipkart.drift.commons.model.enums.Version;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

import static com.flipkart.drift.commons.utils.Constants.GroovyParser.GROOVY_PARSER_FILTER_METHOD_NOT_STARTS_WITH_PREFIX;

@Slf4j
public class Utility {

    private Utility() {
    }

    public static synchronized String generateRandomId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    public static String getTemplatizedFieldName(Field field) {
        final String name = field.getName();
        final boolean isBoolean = (field.getType() == Boolean.class || field.getType() == boolean.class);
        String getterMethodName = (isBoolean ? "is" : "get") + name.substring(0, 1).toUpperCase() + name.substring(1);
        return GROOVY_PARSER_FILTER_METHOD_NOT_STARTS_WITH_PREFIX + getterMethodName;
    }


    public static String getMD5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(text.getBytes());
            byte[] digest = md.digest();
            StringBuffer sb = new StringBuffer();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Error while generating md5 string for string: " + text, e);
            return "";
        }
    }


    public static String generateRowKey(String id, Object version) {
        if (version instanceof Version) {
            return id + "_" + version;
        } else if (version instanceof Integer || version instanceof String) {
            return id + "_" + version;
        } else {
            throw new IllegalArgumentException("Version must be either an instance of Version enum or Integer");
        }
    }

    public static Integer StringToIntegerVersionParser(String version) {
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public static Object parseVersion(String version) {
        if (Objects.equals(version, Version.SNAPSHOT.toString()) || Objects.equals(version, Version.LATEST.toString()) || Objects.equals(version, Version.ACTIVE.toString())) {
            return Version.valueOf(version);
        } else {
            return Integer.parseInt(version);
        }
    }
}
