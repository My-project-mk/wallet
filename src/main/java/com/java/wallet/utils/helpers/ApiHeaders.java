package com.java.wallet.utils.helpers;

public final class ApiHeaders {

//    "true" when a POST response is a replay of an earlier request with the same idempotency key
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private ApiHeaders() {
    }
}
