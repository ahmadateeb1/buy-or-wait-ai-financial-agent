package com.buyorwait.controller;

import com.buyorwait.model.AnalysisRequest;
import com.buyorwait.model.AnalysisResponse;
import com.buyorwait.model.Verdict;
import com.buyorwait.service.OpenAiDecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {

    @Test
    void acceptsCompletePurchaseAnalysisRequest() throws Exception {
        AnalysisResponse response = new AnalysisResponse(
                Verdict.WAIT,
                82,
                "Affordable, but the timing is not ideal.",
                List.of(
                        "The price is high relative to monthly income.",
                        "The purchase can wait."));
        AtomicReference<AnalysisRequest> receivedRequest = new AtomicReference<>();
        OpenAiDecisionService decisionService = new OpenAiDecisionService(
                null, "test-model") {
            @Override
            public AnalysisResponse analyze(AnalysisRequest request) {
                receivedRequest.set(request);
                return response;
            }
        };
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalysisController(decisionService))
                .build();

        String requestBody = """
                {
                  "financialProfile": {
                    "incomeType": "SALARIED",
                    "monthlyIncome": 5000,
                    "monthlyExpenses": [
                      {"category": "HOUSING", "amount": 1200},
                      {"category": "GROCERIES", "amount": 450}
                    ],
                    "liquidSavings": 10000,
                    "monthlyDebtPayments": 350
                  },
                  "purchase": {
                    "productName": "Sony WH-1000XM6",
                    "price": 449.99,
                    "currency": "USD",
                    "alreadyOwnSimilarProduct": false,
                    "usageFrequency": "DAILY",
                    "urgency": 7
                  }
                }
                """;

        mockMvc.perform(post("/api/analysis")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("$.verdict").value("WAIT"))
                .andExpect(jsonPath("$.confidence").value(82))
                .andExpect(jsonPath("$.summary").value(
                        "Affordable, but the timing is not ideal."))
                .andExpect(jsonPath("$.reasons.length()").value(2))
                .andExpect(jsonPath("$.reasons[0]").value(
                        "The price is high relative to monthly income."));

        assertNotNull(receivedRequest.get());
        assertEquals("Sony WH-1000XM6",
                receivedRequest.get().purchase().productName());
    }

    @Test
    void returnsBadGatewayWithoutExposingOpenAiFailure() throws Exception {
        OpenAiDecisionService decisionService = new OpenAiDecisionService(
                null, "test-model") {
            @Override
            public AnalysisResponse analyze(AnalysisRequest request) {
                throw new IllegalStateException("internal SDK details");
            }
        };
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalysisController(decisionService))
                .build();

        mockMvc.perform(post("/api/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "financialProfile": {
                                    "incomeType": "SALARIED",
                                    "monthlyIncome": 5000,
                                    "monthlyExpenses": [],
                                    "liquidSavings": 10000,
                                    "monthlyDebtPayments": 350
                                  },
                                  "purchase": {
                                    "productName": "Headphones",
                                    "price": 449.99,
                                    "currency": "USD",
                                    "alreadyOwnSimilarProduct": false,
                                    "usageFrequency": "DAILY",
                                    "urgency": 7
                                  }
                                }
                """))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("internal SDK details"))));
    }
}
