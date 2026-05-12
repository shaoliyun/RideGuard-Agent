package com.fancy.taxiagent.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagQAAddDTO {
    private List<String> questions;
    private String answer;
}
