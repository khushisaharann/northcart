package com.northcart.common.dto;

/** Simple {"message": "..."} body for endpoints that have nothing else to return. */
public record MessageResponse(String message) {
}
