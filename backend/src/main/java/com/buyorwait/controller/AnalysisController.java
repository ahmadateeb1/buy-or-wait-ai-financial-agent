package com.buyorwait.controller;

import com.buyorwait.model.AnalysisRequest;
import com.buyorwait.model.AnalysisResponse;
import com.buyorwait.service.OpenAiDecisionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class AnalysisController {

    private final OpenAiDecisionService decisionService;

    public AnalysisController(OpenAiDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping("/analysis")
    public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
        try {
            return decisionService.analyze(request);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Analysis service is temporarily unavailable");
        }
    }
}
