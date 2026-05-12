package com.fancy.taxiagent.domain.dto;

import lombok.Data;

@Data
public class RagQAQueryDTO {
    private Integer page;
    private Integer size;
    private String groupId;
}
