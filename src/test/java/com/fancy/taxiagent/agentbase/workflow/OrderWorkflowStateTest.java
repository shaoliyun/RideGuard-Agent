package com.fancy.taxiagent.agentbase.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderWorkflowStateTest {
    @Test
    void acceptsHappyPath() {
        assertTrue(OrderWorkflowState.COLLECTING.canTransitionTo(OrderWorkflowState.QUOTED));
        assertTrue(OrderWorkflowState.QUOTED.canTransitionTo(OrderWorkflowState.WAITING_CONFIRMATION));
        assertTrue(OrderWorkflowState.WAITING_CONFIRMATION.canTransitionTo(OrderWorkflowState.CREATING));
        assertTrue(OrderWorkflowState.CREATING.canTransitionTo(OrderWorkflowState.CREATED));
    }

    @Test
    void rejectsSkippingConfirmationAndLeavingTerminalState() {
        assertFalse(OrderWorkflowState.QUOTED.canTransitionTo(OrderWorkflowState.CREATED));
        assertFalse(OrderWorkflowState.CREATED.canTransitionTo(OrderWorkflowState.COLLECTING));
    }
}
