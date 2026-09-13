package com.buyorwait.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class DatasetFixture {
    private DatasetFixture() {
    }

    static Path create(Path directory) throws IOException {
        Files.writeString(directory.resolve("financial_profiles.csv"), """
                user_id,home_currency,current_available_balance,minimum_balance_to_keep,financial_priorities,expense_categories_to_protect,expense_categories_user_is_willing_to_reduce,expense_categories_user_is_willing_to_stop,payment_methods_user_will_consider,max_installment_months
                user_1,INR,123.45,50,education,rent,dining,streaming,full_payment|installments,3
                """);
        Files.writeString(directory.resolve("financial_events.csv"), """
                event_id,user_id,event_type,description,category,direction,amount,currency,event_date,settlement_date,status,linked_event_id,flexibility,minimum_allowed_amount
                event_1,user_1,expense,Receipt-backed bill,utilities,debit,,INR,2026-01-01,,pending,,fixed,
                """);
        Files.writeString(directory.resolve("exchange_rates.csv"), """
                rate_date,from_currency,to_currency,rate
                2026-01-01,USD,INR,85.25
                """);
        Files.writeString(directory.resolve("requests.csv"), """
                request_id,user_id,request_date,request_type,requested_amount,desired_completion_date,allows_partial_payment,request_text
                request_1,user_1,2026-01-01,purchase,100,2026-01-15,true,Can I buy this?
                """);
        Files.writeString(directory.resolve("request_payment_options.csv"), """
                payment_option_id,request_id,payment_method,payment_amount,number_of_payments,first_payment_date,payment_frequency_days,financing_fee,total_payable_amount
                option_1,request_1,full_payment,100,1,2026-01-01,,0,100
                """);
        Files.writeString(directory.resolve("messages.csv"), """
                message_id,user_id,request_id,related_event_id,sent_at,source_type,message_text
                message_1,user_1,request_1,event_1,2026-01-01T09:30:00Z,bank,Reference only
                """);
        Files.writeString(directory.resolve("images.csv"), """
                image_id,user_id,request_id,related_event_id
                image_1,user_1,request_1,event_1
                """);
        Files.writeString(directory.resolve("sample_requests.csv"), """
                request_id,user_id,request_date,request_type,requested_amount,desired_completion_date,allows_partial_payment,request_text,amount_safe_to_pay,affordability_status,recommended_payment_method,payment_plan,earliest_date_for_full_payment,spending_changes_needed,decision_explanation
                sample_1,user_1,2026-01-01,purchase,100,2026-01-15,true,Sample,100,affordable_now,full_payment,2026-01-01:100,2026-01-01,none,Sample outcome
                """);
        return directory;
    }
}
