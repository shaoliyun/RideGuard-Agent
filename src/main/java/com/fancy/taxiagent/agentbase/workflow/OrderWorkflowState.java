package com.fancy.taxiagent.agentbase.workflow;

import java.util.EnumSet;
import java.util.Set;

public enum OrderWorkflowState {
    COLLECTING,
    QUOTED,
    WAITING_CONFIRMATION,
    CREATING,
    CREATED,
    CANCELLED,
    EXPIRED,
    FAILED;

    public Set<OrderWorkflowState> allowedNextStates() {
        return switch (this) {
            case COLLECTING -> EnumSet.of(QUOTED, CANCELLED, EXPIRED, FAILED);
            case QUOTED -> EnumSet.of(COLLECTING, WAITING_CONFIRMATION, CANCELLED, EXPIRED, FAILED);
            case WAITING_CONFIRMATION -> EnumSet.of(COLLECTING, CREATING, CANCELLED, EXPIRED, FAILED);
            case CREATING -> EnumSet.of(CREATED, FAILED);
            case FAILED -> EnumSet.of(COLLECTING, CREATING, CANCELLED, EXPIRED);
            case CREATED, CANCELLED, EXPIRED -> EnumSet.noneOf(OrderWorkflowState.class);
        };
    }

    public boolean canTransitionTo(OrderWorkflowState target) {
        return target != null && allowedNextStates().contains(target);
    }
}
