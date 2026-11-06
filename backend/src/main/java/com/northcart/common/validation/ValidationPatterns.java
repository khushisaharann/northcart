package com.northcart.common.validation;

/**
 * Regexes shared by request DTOs. Annotation attributes must be compile-time constants,
 * hence static final Strings.
 */
public final class ValidationPatterns {

    /** At least one letter and one digit. Length (8-72) is checked separately with @Size. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";

    /** Digits, spaces, '+' and '-', 7-20 characters, e.g. "+91 98765-43210". */
    public static final String PHONE = "^[0-9+\\- ]{7,20}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number";

    /**
     * BCrypt only uses the first 72 BYTES of a password; anything longer is silently ignored.
     * We cap the length so users aren't misled into thinking a longer password is stronger.
     */
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;

    private ValidationPatterns() {
    }
}
