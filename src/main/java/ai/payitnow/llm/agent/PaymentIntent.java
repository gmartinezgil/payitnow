package ai.payitnow.llm.agent;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

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

    //
    private String accountNumber;// CLABE or Account No
    private String routingNumber;// SWIFT/BIC or Routing No
    private String bankName;// e.g. "BBVA"

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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getRoutingNumber() {
        return routingNumber;
    }

    public void setRoutingNumber(String routingNumber) {
        this.routingNumber = routingNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PaymentIntent that = (PaymentIntent) o;
        return isComplete == that.isComplete && Objects.equals(intent, that.intent) && Objects.equals(amount, that.amount) && Objects.equals(currency, that.currency) && Objects.equals(recipient, that.recipient) && Objects.equals(country, that.country) && Objects.equals(beneficiary, that.beneficiary) && Objects.equals(accountNumber, that.accountNumber) && Objects.equals(routingNumber, that.routingNumber) && Objects.equals(bankName, that.bankName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intent, amount, currency, recipient, country, beneficiary, isComplete, accountNumber, routingNumber, bankName);
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
                ", accountNumber='" + accountNumber + '\'' +
                ", routingNumber='" + routingNumber + '\'' +
                ", bankName='" + bankName + '\'' +
                '}';
    }

}