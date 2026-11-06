package com.northcart.cart.dto;

/**
 * Why a cart line can't be checked out right now. Stock and availability can change after
 * the customer adds something, so the cart REPORTS problems instead of failing to load.
 */
public enum CartItemIssue {
    /** Product deactivated or its category hidden. Must be removed. */
    UNAVAILABLE,
    /** No stock at all. */
    OUT_OF_STOCK,
    /** Some stock, but less than the quantity in the cart. */
    INSUFFICIENT_STOCK
}
