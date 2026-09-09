package com.fancy.taxiagent.agentbase.capability;

import java.util.Set;

public record ExecutionContext(
        String requestId,
        String tenantId,
        String userId,
        String chatId,
        Set<String> permissionScopes) {

    public ExecutionContext {
        permissionScopes = permissionScopes == null ? Set.of() : Set.copyOf(permissionScopes);
    }

    public boolean hasAllScopes(Set<String> requiredScopes) {
        return permissionScopes.containsAll(requiredScopes);
    }
}
