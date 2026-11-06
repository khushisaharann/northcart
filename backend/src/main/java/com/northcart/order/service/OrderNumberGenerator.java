package com.northcart.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Public order ids like "NC-20261106-7F3K9A".
 *
 * <ul>
 *   <li>Not the database id: sequential ids reveal sales volume and invite guessing
 *       (/orders/1001, /orders/1002 ...).</li>
 *   <li>Readable over the phone: the alphabet leaves out 0/O and 1/I.</li>
 *   <li>32^6 ≈ 1 billion combinations per day; uk_orders_order_number is the final guard.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // Plain "uuuuMMdd". Not BASIC_ISO_DATE: once a zone is attached, that one appends the offset
    // ("20261106Z"). Dated in India time, the shop's business day (same as the dashboard).
    private static final DateTimeFormatter DATE =
           DateTimeFormatter.ofPattern("uuuuMMdd").withZone(ZoneId.of("Asia/Kolkata"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    public String next() {
        StringBuilder sb = new StringBuilder("NC-").append(DATE.format(clock.instant())).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
