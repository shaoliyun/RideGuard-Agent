package com.fancy.taxiagent.agentbase.capability;

import com.fancy.taxiagent.agentbase.tool.RagTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagSearchCapabilityTest {
    @Test
    void exposesReadOnlyMetadataAndDelegatesWithExecutionContext() {
        RagTool ragTool = mock(RagTool.class);
        when(ragTool.searchKnowledgeBase(eq("退款规则"), argThat(this::hasUserAndChatContext)))
                .thenReturn("知识库结果");

        RagSearchCapability capability = new RagSearchCapability(ragTool);
        CapabilityResult<String> result = capability.execute(
                "退款规则",
                new ExecutionContext("request-1", null, "user-1", "chat-1", Set.of()));

        assertEquals("knowledge.search", capability.name());
        assertEquals(String.class, capability.inputType());
        assertEquals(String.class, capability.outputType());
        assertEquals(RiskLevel.READ_ONLY, capability.riskLevel());
        assertFalse(capability.requiresConfirmation());
        assertTrue(result.success());
        assertEquals("知识库结果", result.data());
        verify(ragTool).searchKnowledgeBase(eq("退款规则"), argThat(this::hasUserAndChatContext));
    }

    @Test
    void rejectsBlankQuery() {
        RagTool ragTool = mock(RagTool.class);
        RagSearchCapability capability = new RagSearchCapability(ragTool);

        CapabilityResult<String> result = capability.execute(
                " ", new ExecutionContext("request-1", null, "user-1", "chat-1", Set.of()));

        assertFalse(result.success());
        assertEquals("INVALID_INPUT", result.errorCode());
    }

    private boolean hasUserAndChatContext(ToolContext context) {
        return "user-1".equals(context.getContext().get("userId"))
                && "chat-1".equals(context.getContext().get("chatId"));
    }
}