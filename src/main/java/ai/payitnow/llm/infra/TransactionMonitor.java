package ai.payitnow.llm.infra;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.bson.Document;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TransactionMonitor {

    private final MongoCollection<Document> swapCollection;
    private final SwapService swapService;
    private final TelegramBot bot;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public TransactionMonitor(MongoCollection<Document> collection, SwapService service, TelegramBot bot) {
        this.swapCollection = collection;
        this.swapService = service;
        this.bot = bot;
    }

    public void start() {
        // Run every 60 seconds
        scheduler.scheduleAtFixedRate(this::checkTransactions, 0, 60, TimeUnit.SECONDS);
        System.out.println("🕵️ Transaction Monitor Started");
    }

    private void checkTransactions() {
        // Find swaps that are NOT done yet
        // Statuses to watch: "wait" (waiting for deposit), "confirmation", "exchange", "sending"
        for (Document doc : swapCollection.find(Filters.in("status", "wait", "confirmation", "exchange", "sending"))) {

            String txId = doc.getString("tx_id");
            Long userId = doc.getLong("user_id");
            String oldStatus = doc.getString("status");

            // 1. Check API
            String newStatus = swapService.getTransactionStatus(txId);

            // 2. If status changed, update DB and Notify User
            if (!newStatus.equals("error") && !newStatus.equals(oldStatus)) {

                // Update DB
                swapCollection.updateOne(Filters.eq("tx_id", txId), Updates.set("status", newStatus));

                // Notify via Telegram
                String message = getStatusMessage(newStatus, doc);
                if (message != null) {
                    bot.execute(new SendMessage(userId, message));
                }
            }
            // --- NEW: HANDLE FIAT PAYOUTS ---
            if ("payout_processing".equals(doc.getString("status"))) {
                // In a real app, you would poll Circle's GET /v1/payouts/{id}
                // For MVP, we auto-complete it after 30 seconds.
                long createdAt = doc.getLong("created_at");
                if (System.currentTimeMillis() - createdAt > 30_000) {

                    // 1. Update DB
                    swapCollection.updateOne(Filters.eq("tx_id", txId), Updates.set("status", "settled"));

                    // 2. Notify User
                    userId = doc.getLong("user_id");
                    String amount = doc.getString("amount_expected");
                    String currency = doc.getString("pair").split("->")[1]; // "MXN"

                    bot.execute(new SendMessage(userId,
                            String.format("🏦 MONEY DEPOSITED!\n\nThe payout %s has been settled.\n%s %s is now in the beneficiary's bank account.",
                                    txId, amount, currency)));
                }
            }
        }
    }

    private String getStatusMessage(String status, Document doc) {
        String pair = doc.getString("pair");
        switch (status) {
            case "confirmation":
                return "DETECTED: We see your deposit for " + pair + ". Waiting for confirmations... ⛓️";
            case "exchange":
                return "SWAPPING: Confirmations done. Exchanging now... 🔄";
            case "sending":
                return "SENDING: Swap complete. Sending funds to your wallet... 💸";
            case "success":
                return "✅ COMPLETE! Your " + pair + " swap is finished. Check your wallet balance.";
            case "overdue":
                return "⚠️ TIMEOUT: We did not receive your deposit in time. The transaction " + doc.getString("tx_id") + " is cancelled. DO NOT SEND FUNDS.";
            case "refund":
                return "↩️ REFUNDED: There was an issue and funds are being returned to you.";
            default:
                return null; // Don't spam for unknown intermediate states
        }
    }
}