package io.oci.util;

import java.util.regex.Pattern;

public class StringValidationUtil {
    
    private static final Pattern DIGEST_PATTERN = Pattern.compile("^[a-zA-Z0-9]+:[a-fA-F0-9]+$");
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("^[a-z0-9]+(?:[._-][a-z0-9]+)*$");
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}$");
    private static final Pattern UPLOAD_UUID_PATTERN = Pattern.compile("^[a-fA-F0-9-]{36}$");

    private StringValidationUtil() {
        // Private constructor to prevent instantiation
    }

    public static boolean isValidDigest(String digest) {
        return digest != null && DIGEST_PATTERN.matcher(digest).matches();
    }

    public static boolean isValidRepositoryName(String name) {
        return name != null && REPOSITORY_NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isValidTag(String tag) {
        return tag != null && TAG_PATTERN.matcher(tag).matches();
    }

    public static boolean isValidUploadUuid(String uuid) {
        return uuid != null && UPLOAD_UUID_PATTERN.matcher(uuid).matches();
    }

    public static String sanitizeRepositoryName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim().toLowerCase();
    }

    public static String sanitizeTag(String tag) {
        if (tag == null) {
            return null;
        }
        return tag.trim();
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isNullOrBlank(String str) {
        return str == null || str.trim().isBlank();
    }
}