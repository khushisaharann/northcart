package com.northcart.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns names into URL-friendly identifiers: "Café Mug (Set of 4)!" -> "cafe-mug-set-of-4".
 * Slugs make URLs readable (/products/wireless-mouse) and are good for SEO.
 */
public final class SlugUtils {

    private static final int MAX_BASE_LENGTH = 150; // leaves room for a "-2" suffix within 180

    private SlugUtils() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "item";
        }
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");                 // strip accents: é -> e
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")             // anything else becomes a dash
                .replaceAll("(^-+|-+$)", "");              // no leading/trailing dashes
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    /**
     * Appends -2, -3 ... until {@code isTaken} says the slug is free.
     * The UNIQUE constraint remains the final guard against two concurrent requests.
     */
    public static String uniqueSlug(String input, Predicate<String> isTaken) {
        String base = slugify(input);
        String candidate = base;
        for (int n = 2; isTaken.test(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
