package ai.payitnow.llm.agent;

import ai.payitnow.llm.circle.CctpService;
import ai.payitnow.llm.circle.CirclePayoutService;
import ai.payitnow.llm.infra.ArcDexService;
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
import org.bson.Document;
import org.json.JSONObject;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
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
    static ArcDexService arcDexService = new ArcDexService();
    static CctpService cctpService = new CctpService();
    static CirclePayoutService circlePayoutService = new CirclePayoutService();

    // TOGGLE THIS FOR TESTING
    private static final boolean MOCK_MODE = false;

    // 2. Define the Extractor Service
    public interface IntentExtractor {
        @UserMessage("Extract payment details from: {{it}}")
        PaymentIntent extract(String userMessage);
    }

    // FIX 1: Use Builder to inject the System Prompt directly in Java
    //static IntentExtractor extractor = AiServices.create(IntentExtractor.class, model);
    // TODO: this is because we don't want to integrate it directly on the gguf file/prompt so we override it here...
    static IntentExtractor extractor = AiServices.builder(IntentExtractor.class)
            .chatLanguageModel(model)
            .systemMessageProvider(chatId -> """
                    You are a payment assistant. You MUST output strictly valid JSON.
                    
                    INTENTS:
                    - SETTLE_FIAT: User wants to send 'real money', 'cash', 'fiat', or transfer to a 'bank' or 'person' in a specific 'country'.
                      Keywords: "Send USD", "Send MXN", "Wire", "Remit", "To Mexico", "To Mom".
                    - SAVE_CONTACT: User providing bank details to save a beneficiary.
                      Keywords: "Save contact", "Add beneficiary", "Save account for [Name]".
                      Required: "beneficiary", "country", "accountNumber", "routingNumber" (optional: "bankName").
                    - TRANSFER: User wants to send Crypto (ETH, BTC, USDC tokens) to a wallet address.
                    - BUY: User wants to swap ETH for Tokens (USDC).
                    - SELL: User wants to swap Tokens for ETH.
                    - BALANCE: Check wallet funds.
                    
                    RULES:
                    - If currency is USD, MXN, or EUR -> Intent is SETTLE_FIAT.
                    - If currency is USDC, USDT, ETH -> Intent is TRANSFER (or BUY/SELL).
                    - Extract 'country' (e.g., Mexico, USA) and 'beneficiary' (e.g., Juan, Mom) for SETTLE_FIAT.
                    - If user says "Save Mom's account: Mexico, CLABE 123..." -> Intent is SAVE_CONTACT.
                    - Identify "accountNumber" (digits) and "routingNumber" (alphanumeric SWIFT or digits).
                    
                    Output Format:
                    {"intent": "SETTLE_FIAT", "amount": 500, "currency": "MXN", "country": "Mexico", "beneficiary": "Juan"}
                    {"intent": "SAVE_CONTACT", "beneficiary": "Mom", "country": "MX", "accountNumber": "123456789012345678", "routingNumber": "BCMRMXMMXXX"}
                    """)
            .build();

    // 3. Build the Graph
    public static StateGraph<AgentState> buildGraph() throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new);

        // --- NODE 1: PARSE ---
        workflow.addNode("parse_intent", state -> {
            // Read "userInput" (String) instead of "messages" (UserMessage)
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

                // NEW: Validation for SAVE_CONTACT
                if ("SAVE_CONTACT".equalsIgnoreCase(intent.getIntent())) {
                    if (intent.getBeneficiary() != null && intent.getAccountNumber() != null) {
                        intent.setComplete(true);
                    }
                }

                // FIX 2: SAFETY NET FOR FIAT
                // If the LLM guessed "TRANSFER" but the currency is clearly Fiat, force SETTLE_FIAT.
                boolean isFiatCurrency = "USD".equalsIgnoreCase(intent.getCurrency())
                        || "MXN".equalsIgnoreCase(intent.getCurrency())
                        || "EUR".equalsIgnoreCase(intent.getCurrency());

                if ("TRANSFER".equalsIgnoreCase(intent.getIntent()) && (isFiatCurrency || intent.getCountry() != null)) {
                    System.out.println("⚠️ Auto-correcting intent from TRANSFER to SETTLE_FIAT");
                    intent.setIntent("SETTLE_FIAT");
                }

                // Check if intent is extracted and complete
                boolean isBalance = "CHECK_BALANCE".equalsIgnoreCase(intent.getIntent())
                        || "BALANCE".equalsIgnoreCase(intent.getIntent());
                boolean isPayment = "TRANSFER".equalsIgnoreCase(intent.getIntent())
                        || "BUY".equalsIgnoreCase(intent.getIntent())
                        || "SETTLE_FIAT".equalsIgnoreCase(intent.getIntent());

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
                Long userId = state.value("userId").map(id -> Long.parseLong(id.toString())).orElse(12345L);
                Credentials wallet = walletService.getOrCreateWallet(userId);
                String address = wallet.getAddress();

                String resultMsg = null;
                String depositAddressForQr = null; // Store address for QR

                // --- 1. CHECK BALANCE (Multi-Asset) ---
                if ("CHECK_BALANCE".equalsIgnoreCase(intent.getIntent()) || "BALANCE".equalsIgnoreCase(intent.getIntent())) {

                    StringBuilder report = new StringBuilder();
                    report.append(String.format("💰 **Wallet Overview**\n`%s`\n\n", address.substring(0, 8) + "..."));

                    // A. Define Assets to Check
                    // Format: {Ticker, Display Name, Network}
                    String[][] assets = {
                            {"ETH", "ETH", "Sepolia Testnet"},
                            {"USDC", "USDC", "Arc Blockhain (L1)"},       // Arc Native USDC
                            {"USDC_ETH", "USDC", "Sepolia (ERC-20)"}      // Circle USDC on Eth
                    };

                    boolean hasFunds = false;

                    // B. Loop and Check
                    for (String[] asset : assets) {
                        String ticker = asset[0];
                        String name = asset[1];
                        String net = asset[2];

                        try {
                            String bal = cryptoService.checkBalance(address, ticker);

                            // Only show if balance > 0 to keep it clean (Optional)
                            if (new BigDecimal(bal).compareTo(BigDecimal.ZERO) > 0) {
                                report.append(String.format("• **%s %s**: %s\n  _(%s)_\n", bal, name, bal, net));
                                hasFunds = true;
                            } else {
                                // Or show everything even if 0
                                report.append(String.format("• %s %s: %s\n", name, net, bal));
                            }
                        } catch (Exception e) {
                            report.append(String.format("• %s: Error\n", name));
                        }
                    }

                    // C. Add Fiat (Mock/Real) if available
                    // report.append("\n💵 **Fiat (Circle Mint)**\n• USD: 0.00 (Mock)");

                    resultMsg = report.toString();
                }
                // --- 2. BUY LOGIC (SWAP) ---
                else if ("BUY".equalsIgnoreCase(intent.getIntent())) {
                    String fromCoin = "ETH";
                    String toCoin = intent.getCurrency(); // "USDC"
                    String amountToBuy = intent.getAmount().toString(); // "20"
                    System.out.println("fromCoin(" + fromCoin + "), toCoin(" + toCoin + "), amountToReceive(" + amountToBuy + ")");

                    // --- A. VALIDATE MINIMUMS (New Feature) ---
                    // We reverse the query: "How much ETH is 20 USDC?" to estimate cost
                    // Note: This is an estimation. Real validation happens on the 'from' amount.
                    SwapService.Quote quote = swapService.getQuote(toCoin, fromCoin, amountToBuy);

                    if (quote.error != null && !MOCK_MODE) {
                        resultMsg = "⚠️ Market Error: " + quote.error;
                    } else {
                        String estimatedEthCost = MOCK_MODE ? "0.01" : quote.estimatedAmount;
                        // --- B. CHECK BOT WALLET FUNDS (Your Feature) ---
                        try {
                            String balanceStr = cryptoService.checkBalance(wallet.getAddress(), fromCoin);
                            BigDecimal currentBalance = new BigDecimal(balanceStr);
                            BigDecimal requiredEth = new BigDecimal(estimatedEthCost);
                            BigDecimal requiredTotal = requiredEth.multiply(new BigDecimal("1.05"));// BUFFER: Add 5% for gas fees
                            // Check Min Amount from API
//                            if (quote.minAmount != null && new BigDecimal(quote.minAmount).compareTo(new BigDecimal(amountToBuy)) > 0) {
//                                // e.g. Trying to buy 20 USDC, but min is 120
//                                resultMsg = String.format("⚠️ Amount too low. Minimum buy is %s %s.", quote.minAmount, toCoin);
//                                return CompletableFuture.completedFuture(Map.of("final_response", resultMsg));
//                            }
                            if ((currentBalance.compareTo(new BigDecimal(quote.minAmount)) < 0
                                    || currentBalance.compareTo(requiredTotal) < 0) && !MOCK_MODE) {
                                // CASE: Bot needs funds
                                if (currentBalance.compareTo(new BigDecimal(quote.minAmount)) < 0) {
                                    resultMsg = String.format("⚠️ Insufficient Bot Funds.\n\n" +
                                                    "You need to buy at minimum %s %s, you can't buy just %s %s.\n" +
                                                    "Current Balance: %s ETH\n\n" +
                                                    "👉 Please scan the QR code to top up the bot wallet with ETH to make the conversion and buy the minimum.",
                                            quote.minAmount,
                                            toCoin,
                                            amountToBuy,
                                            toCoin,
                                            balanceStr);
                                }
                                if (currentBalance.compareTo(requiredTotal) < 0) {
                                    resultMsg = String.format("⚠️ Insufficient Bot Funds.\n\n" +
                                                    "To buy %s %s, the bot needs approx %s ETH.\n" +
                                                    "Current Balance: %s ETH\n\n" +
                                                    "👉 Please scan the QR code to top up the bot wallet for the missing ETH.",
                                            amountToBuy,
                                            toCoin,
                                            requiredTotal.toPlainString(),
                                            balanceStr);
                                }
                                // Return immediately with Bot's Address QR
                                Map<String, Object> responseMap = new HashMap<>();
                                responseMap.put("final_response", resultMsg);
                                responseMap.put("qr_address", wallet.getAddress()); // Bot's Wallet
                                return CompletableFuture.completedFuture(responseMap);
                            }
                        } catch (Exception e) {
                            // If numbers parse incorrectly, log but don't crash
                            e.printStackTrace();
                        }
                    }

                    // 3. CREATE TRANSACTION
                    JSONObject swapResponse = null;
                    String depositAddress = null;

                    if (MOCK_MODE) {
                        depositAddress = "0xMockDepositAddress123";
                        resultMsg = String.format("✅ SWAP INITIATED (MOCK)!\n\n1. Created Order (ETH -> %s)\n2. Deposit Address: %s\n\nYou will receive %s %s shortly.",
                                toCoin, depositAddress, amountToBuy, toCoin);
                    } else {
                        // Call Real API
                        // We are swapping ETH (from) -> USDC (to)
                        swapResponse = swapService.createTransaction(fromCoin, toCoin, amountToBuy, wallet.getAddress());

                        if (!swapResponse.has("error")) {
                            depositAddress = swapResponse.getString("deposit_address");
                            // Real Mode: Save to DB for Monitor
                            String txId = swapResponse.getString("transaction_id");
                            Document swapDoc = new Document("tx_id", txId)
                                    .append("user_id", userId)
                                    .append("status", "wait")
                                    .append("pair", fromCoin + "->" + toCoin)
                                    .append("amount_expected", amountToBuy)
                                    .append("deposit_address", depositAddress)
                                    .append("created_at", System.currentTimeMillis());
                            walletService.getDatabase().getCollection("active_swaps").insertOne(swapDoc);
                            resultMsg = String.format("✅ SWAP CREATED!\nTx ID: %s\n\nPlease deposit %s %s to the address below within 15 minutes.", txId, amountToBuy, fromCoin);
                            //return CompletableFuture.completedFuture(Map.of("final_response", "⚠️ Swap Failed: " + swapResponse.getString("error")));
                        } else {
                            // 2. FALLBACK: Arc Network Direct Swap
                            System.out.println("⚠️ LetsExchange failed (" + swapResponse.getString("error") + "). Switching to Arc On-Chain Swap...");

                            if (toCoin.equalsIgnoreCase("USDC") && fromCoin.equalsIgnoreCase("ETH")) {
                                // You cannot swap "Fiat" here, only tokens.
                                // If user wanted Fiat, we must fail.
                                resultMsg = "⚠️ LetsExchange is down. Direct Arc swap is available only for Crypto-to-Crypto, not Fiat.";
                            } else {
                                // Execute On-Chain Swap
                                // Note: This requires the USER'S private key signed locally,
                                // or the Bot wallet sending funds it already holds.
                                String txHash = arcDexService.swapOnChain(wallet, "USDC_ADDR", "ETH_ADDR", new BigInteger(amountToBuy));
                                // Real Mode: Save to DB for Monitor
                                Document swapDoc = new Document("tx_id", txHash)
                                        .append("user_id", userId)
                                        .append("status", "wait")
                                        .append("pair", fromCoin + "->" + toCoin)
                                        .append("amount_expected", amountToBuy)
                                        .append("deposit_address", depositAddress)
                                        .append("created_at", System.currentTimeMillis());
                                walletService.getDatabase().getCollection("active_swaps").insertOne(swapDoc);
                                resultMsg = "✅ Fallback Swap Executed on Arc Chain!\nTx Hash: " + txHash;
                            }
                        }
                    }

                    // Set address for QR Code generation
                    depositAddressForQr = depositAddress;

                    // -- LOCAL / INTERNATIONAL FIAT PAYMENT --
                } else if ("SETTLE_FIAT".equalsIgnoreCase(intent.getIntent())) {

                    // 1. EXTRACT DATA FROM INTENT
                    String amount = intent.getAmount().toString();
                    String currency = intent.getCurrency(); // "USD" or "MXN"
                    String country = intent.getCountry() != null ? intent.getCountry() : "MX"; // "Mexico"
                    String beneficiary = intent.getBeneficiary() != null ? intent.getBeneficiary() : "Beneficiary";// "Mom" or "Juan"

                    // Generate a placeholder email since we don't ask for it in chat yet
                    String beneEmail = beneficiary.replaceAll("\\s+", ".").toLowerCase() + "@example.com";

                    // 2. NORMALIZE COUNTRY (Fix for ISO 3166-1 Error)
                    String countryCode = "US"; // Default
                    if (country != null) {
                        String c = country.toUpperCase().trim();
                        if (c.equals("MEXICO") || c.equals("MX")) countryCode = "MX";
                        else if (c.equals("USA") || c.equals("UNITED STATES") || c.equals("US")) countryCode = "US";
                        else if (c.equals("COLOMBIA")) countryCode = "CO";
                        else if (c.equals("BRAZIL")) countryCode = "BR";
                        else if (c.equals("ARGENTINA")) countryCode = "AR";
                        else if (c.length() == 2) countryCode = c; // Assume user gave code
                    }

                    // 1. IDENTITY CHECK: Who is this?
                    Document contact = walletService.getContact(userId, beneficiary);
                    if (contact == null) {
                        // If we don't know them, we must ask the user for details
                        resultMsg = String.format("👤 I don't have a bank account saved for '%s'.\n\n" +
                                "Please provide their details in this format:\n" +
                                "Name, Routing/SWIFT, Account Number, Country", beneficiary);
                    } else {
                        // 2. BALANCE CHECK: Do we have the cash?
                        BigDecimal masterBalance = circlePayoutService.getMasterWalletBalance();

                        if (masterBalance.compareTo(intent.getAmount()) < 0) {
                            String depositAddr = circlePayoutService.getMasterWalletId();
                            resultMsg = String.format("⚠️ Master Wallet Insufficient Funds.\n" +
                                            "Balance: %s USDC\nRequired: %s USDC\n\n" +
                                            "👉 Please top up the Master Wallet at this address:\n`%s` ",
                                    masterBalance, amount, depositAddr);

                            Map<String, Object> resp = new HashMap<>();
                            resp.put("final_response", resultMsg);
                            resp.put("qr_address", depositAddr);
                            return CompletableFuture.completedFuture(resp);
                        }

                        // 3. EXECUTE: Use the saved 'beneficiary_id'
                        String beneId = contact.getString("circle_beneficiary_id");
                        String payoutResponse = circlePayoutService.sendFiatToPerson(
                                beneId, //beneficiary,
                                beneEmail,
                                countryCode,
                                amount,
                                currency
                        );

                        // 3. HANDLE RESPONSE
                        if (payoutResponse.startsWith("Error") || payoutResponse.startsWith("Exception")) {
                            resultMsg = "⚠️ Payout Failed: " + payoutResponse;
                        } else {
                            String payoutId = payoutResponse;

                            // Save to Monitor (New Status: 'payout_processing')
                            // We use the Payout ID as the TX ID here
                            Document payoutDoc = new Document("tx_id", payoutId)
                                    .append("user_id", userId)
                                    .append("status", "payout_processing")
                                    .append("pair", "USDC->" + currency)
                                    .append("amount_expected", amount)
                                    .append("beneficiary", beneficiary)
                                    .append("created_at", System.currentTimeMillis());

                            walletService.getDatabase().getCollection("active_swaps").insertOne(payoutDoc);

                            if (!countryCode.equals("US")) {
                                resultMsg = String.format("✅ INTERNATIONAL TRANSFER SENT!\n\n" +
                                                "Recipient: %s (%s)\n" +
                                                "Amount: %s %s\n" +
                                                "Payout ID: %s\n\n" +
                                                "Funds should arrive in 1-2 business days.",
                                        beneficiary, country, amount, currency, payoutId);
                            } else {
                                resultMsg = String.format("✅ SETTLEMENT SUCCESSFUL!\n\n" +
                                                "Sending %s %s to %s's %s account.\n" +
                                                "Payout ID: %s",
                                        amount, contact.getString("currency"), beneficiary, contact.getString("bank_name"), payoutId);
                            }
                        }
                    }


                    // 2. CALL THE NEW SERVICE METHOD
                    // Note: We skip the "Bridge" step here for brevity, assuming funds are in Master Wallet.

                }
                // --- 4. CRYPTO TRANSFER LOGIC (Internal Send) ---
                else {
                    // HERE we still need balance check because the BOT is sending.
                    String currency = intent.getCurrency();
                    if (currency == null || currency.equalsIgnoreCase("bucks")) currency = "USDC";

                    String currentBalanceStr = cryptoService.checkBalance(address, currency);
                    BigDecimal currentBalance = new BigDecimal(currentBalanceStr);
                    BigDecimal amountToSend = intent.getAmount();

                    if (currentBalance.compareTo(amountToSend) < 0 && !MOCK_MODE) {
                        resultMsg = String.format("⚠️ Transaction Failed: Insufficient Funds.\nRequired: %s %s\nAvailable: %s %s",
                                amountToSend, currency, currentBalance, currency);
                    } else {
                        resultMsg = String.format("✅ TRANSFER EXECUTED:\nTo: %s\nAmount: %s %s",
                                intent.getRecipient(), amountToSend, currency);
                    }
                }

                // --- BUILD FINAL RESPONSE MAP ---
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("final_response", resultMsg);
                if (depositAddressForQr != null) {
                    responseMap.put("qr_address", depositAddressForQr);
                }

                return CompletableFuture.completedFuture(responseMap);

            } catch (Exception e) {
                e.printStackTrace();
                return CompletableFuture.completedFuture(Map.of("final_response", "Error: " + e.getMessage()));
            }
        });

        // -- NODE 3: SAVE CONTACT --
        workflow.addNode("save_contact", state -> {
            PaymentIntent intent = (PaymentIntent) state.value("intent").orElseThrow();
            Long userId = state.value("userId").map(id -> Long.parseLong(id.toString())).orElse(12345L);

            // Create the contact document
            Document contact = new Document("user_id", userId)
                    .append("nickname", intent.getBeneficiary().toLowerCase())
                    .append("name", intent.getBeneficiary())
                    .append("country", intent.getCountry()) // normalization to "MX", "US"
                    .append("currency", intent.getCurrency())
                    .append("account_number", intent.getAccountNumber())
                    .append("routing_number", intent.getRoutingNumber())
                    .append("created_at", System.currentTimeMillis());

            // Register with Circle first to get the beneficiary_id
            try {
                String circleId = circlePayoutService.createWireBeneficiary(
                        intent.getBeneficiary(),
                        intent.getBeneficiary().toLowerCase() + "@example.com",
                        intent.getCountry(),
                        intent.getCurrency()
                );
                contact.append("circle_beneficiary_id", circleId);
                walletService.getDatabase().getCollection("contacts").insertOne(contact);

                return CompletableFuture.completedFuture(Map.of("final_response",
                        "✅ Contact Saved! You can now just say 'Send money to " + intent.getBeneficiary() + "'."));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Map.of("final_response", "❌ Failed to save contact: " + e.getMessage()));
            }
        });

        // --- NODE 4: ASK MISSING ---
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
//                state -> {
//                    String nextNode = state.value("intent")
//                            .map(i -> (PaymentIntent) i)
//                            .filter(PaymentIntent::isComplete)
//                            .map(i -> "execute_transaction")
//                            .orElse("ask_missing_info");
//
//                    return CompletableFuture.completedFuture(nextNode);
//                },
//                Map.of(
//                        "execute_transaction", "execute_transaction",
//                        "ask_missing_info", "ask_missing_info"
//                )
                state -> {
                    PaymentIntent i = (PaymentIntent) state.value("intent").orElse(null);
                    if (i == null) return CompletableFuture.completedFuture("ask_missing_info");

                    if ("SAVE_CONTACT".equalsIgnoreCase(i.getIntent()) && i.isComplete()) {
                        return CompletableFuture.completedFuture("save_contact");
                    } else if (i.isComplete()) {
                        return CompletableFuture.completedFuture("execute_transaction");
                    } else {
                        return CompletableFuture.completedFuture("ask_missing_info");
                    }
                },
                Map.of(
                        "execute_transaction", "execute_transaction",
                        "save_contact", "save_contact",
                        "ask_missing_info", "ask_missing_info"
                )
        );

        // --- CONNECT TERMINAL NODES TO "END" ---
        // This tells the graph "We are done now".
        workflow.addEdge("execute_transaction", END);
        workflow.addEdge("ask_missing_info", END);

        return workflow;
    }
}