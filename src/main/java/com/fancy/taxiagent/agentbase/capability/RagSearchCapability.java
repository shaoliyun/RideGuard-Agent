package com.fancy.taxiagent.agentbase.capability;

import com.fancy.taxiagent.agentbase.tool.RagTool;
import com.fancy.taxiagent.constant.ToolContextKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RagSearchCapability implements AgentCapability<String, String> {
    private final RagTool ragTool;

    @Override
    public String name() {
        return "knowledge.search";
    }

    @Override
    public Class<String> inputType() {
        return String.class;
    }

    @Override
    public Class<String> outputType() {
        return String.class;
    }

    @Override
    public CapabilityResult<String> execute(String query, ExecutionContext context) {
        if (query == null || query.isBlank()) {
            return CapabilityResult.failure("INVALID_INPUT", "查询内容不能为空");
        }

        Map<String, Object> toolContextValues = new HashMap<>();
        toolContextValues.put(ToolContextKeyConstants.USER_ID, context.userId());
        toolContextValues.put(ToolContextKeyConstants.CHAT_ID, context.chatId());
        return CapabilityResult.success(ragTool.searchKnowledgeBase(query, new ToolContext(toolContextValues)));
    }
}