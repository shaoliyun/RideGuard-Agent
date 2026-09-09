package com.fancy.taxiagent.agentbase.capability;

import java.util.Set;

public interface AgentCapability<I, O> {
    String name();

    Class<I> inputType();

    Class<O> outputType();

    default RiskLevel riskLevel() {
        return RiskLevel.READ_ONLY;
    }

    default boolean requiresConfirmation() {
        return riskLevel() == RiskLevel.HIGH;
    }

    default Set<String> requiredScopes() {
        return Set.of();
    }

    CapabilityResult<O> execute(I input, ExecutionContext context);
}
