package ai.payitnow;

/**
 * Hello world!
 *
 */

import ai.payitnow.llm.agent.PaymentAgent;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;

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

        System.out.println("🚀 PayItNow Bot is Live on Telegram!");

        // 3. Listen for Messages
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    long chatId = update.message().chat().id();
                    String userText = update.message().text();

                    try {
                        if (userText.equals("/start")) {
                            bot.execute(new SendMessage(chatId, "👋 Welcome to PayItNow! I can help you Send, Buy, or Sell. \n\nTry: 'Send 50 bucks to Mom'"));
                            continue; // Skip the AI Agent
                        }
                        Map<String, Object> inputs = Map.of("userInput", userText);

                        Optional<AgentState> finalState = agent.invoke(inputs);

                        String replyText = finalState.get().value("final_response")
                                .map(Object::toString)
                                .orElse("Sorry, internal error.");

                        bot.execute(new SendMessage(chatId, replyText));

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