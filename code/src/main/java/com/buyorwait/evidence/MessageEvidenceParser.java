package com.buyorwait.evidence;

import com.buyorwait.model.FinancialMessage;
import java.util.List;

/** Replaceable extraction boundary. Binding and financial policy stay outside any future LLM parser. */
public interface MessageEvidenceParser {
    List<MessageFact> parse(FinancialMessage message);
}
