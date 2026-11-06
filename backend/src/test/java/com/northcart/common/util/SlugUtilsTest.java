package com.northcart.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Wireless Mouse,                 wireless-mouse",
            "'  USB-C Hub 7-in-1  ',         usb-c-hub-7-in-1",
            "Café Mug (Set of 4)!,           cafe-mug-set-of-4",
            "Home & Kitchen,                 home-kitchen",
            "'!!!',                          item"
    })
    void slugify(String input, String expected) {
        assertThat(SlugUtils.slugify(input)).isEqualTo(expected);
    }

    @Test
    void uniqueSlugAppendsCounterWhenTaken() {
        Set<String> taken = Set.of("wireless-mouse", "wireless-mouse-2");
        assertThat(SlugUtils.uniqueSlug("Wireless Mouse", taken::contains)).isEqualTo("wireless-mouse-3");
    }

    @Test
    void longNamesAreTruncated() {
        assertThat(SlugUtils.slugify("a".repeat(300))).hasSize(150);
    }
}
