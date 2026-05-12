package com.fancy.taxiagent.domain.vo;

import com.fancy.taxiagent.agentbase.memory.SimpleMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RestoreChatVO {
    private String chatId;
    private List<SimpleMessage> messages;
}
