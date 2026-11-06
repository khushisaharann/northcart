package com.northcart.common.util;

import java.util.Locale;

/** Builds a safe "contains" pattern for SQL LIKE from user input. */
public final class SearchText {

    public static final char ESCAPE = '\\';

    private SearchText() {
    }

    /**
     * "50%_off" -> "%50\%\_off%". Without escaping, a user typing % or _ would turn them
     * into wildcards (not an injection - the value is still a bind parameter - but wrong results).
     */
    public static String containsPattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
