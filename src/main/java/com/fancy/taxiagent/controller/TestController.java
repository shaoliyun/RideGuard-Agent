package com.fancy.taxiagent.controller;

import com.fancy.taxiagent.agentbase.amap.pojo.georegeo.Geocode;
import com.fancy.taxiagent.agentbase.amap.pojo.georegeo.Regeocode;
import com.fancy.taxiagent.agentbase.amap.pojo.route.AmapRoute;
import com.fancy.taxiagent.agentbase.amap.pojo.search.AmapSearchPoi;
import com.fancy.taxiagent.agentbase.amap.pojo.suggestion.AmapInputTip;
import com.fancy.taxiagent.agentbase.amap.service.AmapGeoRegeoService;
import com.fancy.taxiagent.agentbase.amap.service.AmapInputSuggestionService;
import com.fancy.taxiagent.agentbase.amap.service.AmapRouteService;
import com.fancy.taxiagent.agentbase.amap.service.AmapSearchService;
import com.fancy.taxiagent.agentbase.rag.RagService;

import com.fancy.taxiagent.domain.entity.QaDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/test")
@Slf4j
public class TestController {
    @Resource
    private AmapRouteService amapRouteService;
    @Resource
    private AmapGeoRegeoService amapGeoRegeoService;
    @Resource
    private AmapSearchService amapSearchService;
    @Resource
    private AmapInputSuggestionService amapInputSuggestionService;
    @Resource
    private RagService ragService;

    @PostMapping("/rag")
    public List<QaDocument> testRag(String question) {
        return ragService.searchAnswers(question);
    }
}
