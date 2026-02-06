package ai.payitnow.llm.agent;

import java.io.Serializable;
import java.math.BigDecimal;

//import lombok.Data;
//import lombok.ToString;
//
//@Data
//@ToString
public class PaymentIntent implements Serializable {
    private static final long serialVersionUID = 1L;
    // Intent types: TRANSFER, DEPOSIT, WITHDRAW, BUY, SELL
    private String intent;      // TRANSFER, BUY, SELL, SETTLE_FIAT
    private BigDecimal amount;
    private String currency;    // USDC, USD, MXN, EUR
    private String recipient;   // Crypto Address or Username

    // for Fiat Settlement
    private String country;     // e.g., "Mexico"
    private String beneficiary; // e.g., "Juan Perez"
    private boolean isComplete;

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(String beneficiary) {
        this.beneficiary = beneficiary;
    }

    @Override
    public String toString() {
        return "PaymentIntent{" +
                "intent='" + intent + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", recipient='" + recipient + '\'' +
                ", country='" + country + '\'' +
                ", beneficiary='" + beneficiary + '\'' +
                ", isComplete=" + isComplete +
                '}';
    }
}