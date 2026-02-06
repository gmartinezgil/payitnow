package ai.payitnow;

/**
 * Hello world!
 *
 */

import ai.payitnow.llm.agent.PaymentAgent;
import ai.payitnow.llm.infra.SwapService;
import ai.payitnow.llm.infra.TransactionMonitor;
import ai.payitnow.llm.infra.WalletService;
import ai.payitnow.llm.ui.QrService;
import com.mongodb.client.MongoCollection;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bson.Document;

import java.util.Map;
import java.util.Optional;

public class App {

    // Replace with your Token from @BotFather
    private static final String TELEGRAM_TOKEN = "8198938940:AAHj8EXoJ53Pdx4G-kl6wNtvaGvTyfg2ZLQ";

    public static void main(String[] args) throws Exception {
        // 1. Initialize Graph
        CompiledGraph<AgentState> agent = PaymentAgent.buildGraph().compile();

        // 2. Initialize Telegram
        TelegramBot bot = new TelegramBot(TELEGRAM_TOKEN);
        // This forces Telegram to switch from "Push" mode back to "Polling" mode.
        bot.execute(new DeleteWebhook());

        // Get the collection (assuming you exposed it or created a getter)
        WalletService walletService = new WalletService();
        MongoCollection<Document> swapCol = walletService.getDatabase().getCollection("active_swaps");

        // Start Monitor
        TransactionMonitor monitor = new TransactionMonitor(swapCol, new SwapService(), bot);
        monitor.start();

        System.out.println("🚀 PayItNow Bot is Live on Telegram!");

        // 3. Listen for Messages
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    Long telegramUserId = update.message().from().id();
                    long chatId = update.message().chat().id();
                    String userText = update.message().text();

                    try {
                        if (userText.equals("/start")) {
                            bot.execute(new SendMessage(chatId,
                                    "👋 Welcome to PayItNow! \n" +
                                            "I can help you Send Money to your friends locally or across the world, " +
                                            "Buy or Sell Crypto. \n\n" +
                                            // Keep you have a budget over your money
                                            // Make investment decisions
                                            "Try: 'Send 50 bucks to Mom'"));
                            continue; // Skip the AI Agent
                        }
                        //Map<String, Object> inputs = Map.of("userInput", userText);
                        Map<String, Object> inputs = Map.of(
                                "userInput", userText,
                                "userId", telegramUserId
                        );

                        Optional<AgentState> finalState = agent.invoke(inputs);

                        String replyText = finalState.get().value("final_response")
                                .map(Object::toString)
                                .orElse("Sorry, internal error.");

                        // 2. Check for QR Code Trigger
                        String depositAddress = finalState.get().value("qr_address")
                                .map(Object::toString)
                                .orElse(null);

                        if (depositAddress != null) {
                            // A. Generate QR
                            byte[] qrBytes = QrService.generateQr(depositAddress, 300, 300);

                            if (qrBytes != null) {
                                // B. Send Photo with Caption
                                SendPhoto photoMsg = new SendPhoto(chatId, qrBytes)
                                        .caption(replyText); // The text becomes the image caption
                                bot.execute(photoMsg);
                            } else {
                                // Fallback if QR fails
                                bot.execute(new SendMessage(chatId, replyText));
                            }
                        } else {
                            // 3. Standard Text Message (No QR needed)
                            bot.execute(new SendMessage(chatId, replyText));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        bot.execute(new SendMessage(chatId, "⚠️ Error: " + e.getMessage()));
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }
}