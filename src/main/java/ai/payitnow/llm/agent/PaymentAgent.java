package ai.payitnow.llm.agent;

import ai.payitnow.llm.infra.CryptoService;
import ai.payitnow.llm.infra.SwapService;
import ai.payitnow.llm.infra.WalletService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;

public class PaymentAgent {

    // 1. Define the LLM
    static ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("payitnow")
            .temperature(0.1)
            .format("json")
            .build();

    // Initialize Services
    static WalletService walletService = new WalletService();
    static CryptoService cryptoService = new CryptoService();
    static SwapService swapService = new SwapService();

    // 2. Define the Extractor Service
    public interface IntentExtractor {
        @UserMessage("Extract payment details from: {{it}}")
        PaymentIntent extract(String userMessage);
    }

    static IntentExtractor extractor = AiServices.create(IntentExtractor.class, model);

    // 3. Build the Graph
    public static StateGraph<AgentState> buildGraph() throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new);

        // --- NODE 1: PARSE ---
        workflow.addNode("parse_intent", state -> {
            // FIX: Read "userInput" (String) instead of "messages" (UserMessage)
            String userText = state.value("userInput")
                    .map(Object::toString)
                    .orElseThrow(() -> new IllegalStateException("No input found"));

            System.out.println("🤖 Processing: " + userText);

            try {
                PaymentIntent intent = extractor.extract(userText);

                // Check for UNKNOWN or null
                if ("UNKNOWN".equals(intent.getIntent())) {
                    return CompletableFuture.completedFuture(Map.of("error", "I didn't catch that."));
                }

                // Check if intent is extracted and complete
                boolean isBalance = "CHECK_BALANCE".equalsIgnoreCase(intent.getIntent())
                        || "BALANCE".equalsIgnoreCase(intent.getIntent());

                boolean isPayment = "TRANSFER".equalsIgnoreCase(intent.getIntent())
                        || "BUY".equalsIgnoreCase(intent.getIntent());

                if (isBalance) {
                    // Balance checks don't need amounts.
                    // If currency is missing, we will default it later (e.g. to ETH/USDC)
                    intent.setComplete(true);
                } else if (isPayment && intent.getAmount() != null && intent.getCurrency() != null) {
                    // Payments still need strict validation
                    intent.setComplete(true);
                }

                // Return just the intent map (PaymentIntent needs to be Serializable too!)
                return CompletableFuture.completedFuture(Map.of("intent", intent));

            } catch (Exception e) {
                return CompletableFuture.completedFuture(Map.of("error", "Could not understand."));
            }
        });

        // --- NODE 2: EXECUTE ---
        workflow.addNode("execute_transaction", state -> {
            try {
                PaymentIntent intent = (PaymentIntent) state.value("intent").orElseThrow();

                // 1. Get Wallet
                Credentials wallet = walletService.getOrCreateWallet(12345L); // Use real UserID in prod
                String address = wallet.getAddress();
                String resultMsg;

                // 2. LOGIC SWITCH
                if ("CHECK_BALANCE".equalsIgnoreCase(intent.getIntent()) || "BALANCE".equalsIgnoreCase(intent.getIntent())) {
                    String currency = intent.getCurrency() != null ? intent.getCurrency() : "ETH";
                    String balance = cryptoService.checkBalance(address, currency);
                    resultMsg = String.format("💰 Your Wallet (%s)\nBalance: %s %s",
                            address.substring(0, 6) + "...", balance, currency);

                } else if ("BUY".equalsIgnoreCase(intent.getIntent())) {
                    // Check Swap Rates
                    String rateJson = swapService.getExchangeRate("usd", intent.getCurrency(), intent.getAmount().toString());
                    resultMsg = "🔄 Exchange Quote: " + rateJson;
                    // create a transaction here to send funds to Changelly.

                } else {
                    // --- TRANSFER LOGIC ---

                    // A. Default currency if missing (Bucks -> USD -> USDC)
                    String currency = intent.getCurrency();
                    if (currency == null || currency.equalsIgnoreCase("bucks")) currency = "USDC";

                    // B. CHECK BALANCE FIRST
                    String currentBalanceStr = cryptoService.checkBalance(address, currency);
                    BigDecimal currentBalance = new BigDecimal(currentBalanceStr);
                    BigDecimal amountToSend = new BigDecimal(intent.getAmount());

                    // C. COMPARE
                    if (currentBalance.compareTo(amountToSend) < 0) {
                        // FAIL: Not enough money
                        resultMsg = String.format("⚠️ Transaction Failed: Insufficient Funds.\n\n" +
                                        "Required: %s %s\n" +
                                        "Available: %s %s",
                                amountToSend, currency, currentBalance, currency);
                    } else {
                        // PASS: Execute (Mocked for now, real TX later)
                        resultMsg = String.format("✅ TRANSFER EXECUTED:\nFrom: %s\nTo: %s\nAmount: %s %s\n(TxHash: 0x123...mocked)",
                                address, intent.getRecipient(), amountToSend, currency);
                    }
                }

                return CompletableFuture.completedFuture(Map.of("final_response", resultMsg));

            } catch (Exception e) {
                e.printStackTrace();
                return CompletableFuture.completedFuture(Map.of("final_response", "⚠️ Error: " + e.getMessage()));
            }
        });

        // --- NODE 3: ASK MISSING ---
        workflow.addNode("ask_missing_info", state -> {
            return CompletableFuture.completedFuture(
                    Map.of("final_response", "I understood the intent, but I am missing details.")
            );
        });

        // --- EDGES ---
        workflow.setEntryPoint("parse_intent");

        // EdgeAction also usually requires simple string return,
        // but we define the logic clearly here.
        workflow.addConditionalEdges(
                "parse_intent",
                state -> {
                    String nextNode = state.value("intent")
                            .map(i -> (PaymentIntent) i)
                            .filter(PaymentIntent::isComplete)
                            .map(i -> "execute_transaction")
                            .orElse("ask_missing_info");

                    return CompletableFuture.completedFuture(nextNode);
                },
                Map.of(
                        "execute_transaction", "execute_transaction",
                        "ask_missing_info", "ask_missing_info"
                )
        );

        // --- FIX: CONNECT TERMINAL NODES TO "END" ---
        // This tells the graph "We are done now".
        workflow.addEdge("execute_transaction", END);
        workflow.addEdge("ask_missing_info", END);

        return workflow;
    }
}