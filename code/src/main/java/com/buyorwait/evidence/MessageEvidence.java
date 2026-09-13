package com.buyorwait.evidence;

import com.buyorwait.model.FinancialMessage;

public record MessageEvidence(FinancialMessage message, MessageFact fact) { }
