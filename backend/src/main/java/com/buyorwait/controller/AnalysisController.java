package com.buyorwait.controller;

import com.buyorwait.model.AnalysisRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    @PostMapping("/analysis")
    public Map<String, String> analyze(@RequestBody AnalysisRequest request) {
        return Map.of(
                "status", "READY_FOR_ANALYSIS",
                "product", request.purchase().productName()
        );
    }
}
