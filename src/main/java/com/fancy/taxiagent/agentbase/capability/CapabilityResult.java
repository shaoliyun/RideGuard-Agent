package com.fancy.taxiagent.agentbase.capability;

public record CapabilityResult<O>(boolean success, O data, String errorCode, String message) {
    public static <O> CapabilityResult<O> success(O data) {
        return new CapabilityResult<>(true, data, null, null);
    }

    public static <O> CapabilityResult<O> failure(String errorCode, String message) {
        return new CapabilityResult<>(false, null, errorCode, message);
    }
}
