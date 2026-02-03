package ai.payitnow.llm.agent;

import java.io.Serializable;

//import lombok.Data;
//import lombok.ToString;
//
//@Data
//@ToString
public class PaymentIntent implements Serializable {
    private static final long serialVersionUID = 1L;
    // Intent types: TRANSFER, DEPOSIT, WITHDRAW, BUY, SELL
    private String intent;
    private Double amount;
    private String currency;
    private String recipient; // Optional (for Transfers)
    private boolean isComplete; // Internal flag for the graph

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }
}