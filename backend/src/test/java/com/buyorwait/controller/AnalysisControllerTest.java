package com.buyorwait.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {

    @Test
    void acceptsCompletePurchaseAnalysisRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisController()).build();

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
                .andExpect(jsonPath("$.status").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.product").value("Sony WH-1000XM6"));
    }
}
