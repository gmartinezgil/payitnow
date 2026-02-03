package ai.payitnow.llm.train;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class JSONLDataGenerator {

    // MAPPING: Banking77 Labels -> Your Internal Intents
    private static final Map<String, String> BANKING77_MAP = new HashMap<>();

    static {
        BANKING77_MAP.put("card_arrival", "CARD_STATUS");
        BANKING77_MAP.put("card_delivery_estimate", "CARD_STATUS");
        BANKING77_MAP.put("lost_or_stolen_card", "FREEZE_CARD");
        BANKING77_MAP.put("top_up_failed", "SUPPORT_TICKET");
        BANKING77_MAP.put("transfer_not_received_by_recipient", "SUPPORT_TICKET");
        BANKING77_MAP.put("balance_not_updated_after_bank_transfer", "CHECK_BALANCE");
        BANKING77_MAP.put("wrong_exchange_rate_for_cash_withdrawal", "SUPPORT_TICKET");
        BANKING77_MAP.put("declined_card_payment", "SUPPORT_TICKET");
        BANKING77_MAP.put("pending_transfer", "CHECK_STATUS");
    }

    public static void main(String[] args) {
        List<TrainingEntry> allRows = new ArrayList<>();

        System.out.println("Step 1: Generating Synthetic Payment & Trading Data...");
        allRows.addAll(generatePaymentExamples(2000));

        System.out.println("Step 2: Reading Banking77 Data...");
        List<TrainingEntry> realData = readBanking77Csv(System.getProperty("user.dir")
                + File.separator + "src" + File.separator + "main" + File.separator + "resources"
                + File.separator + "training" + File.separator + "test.csv");
        if (realData.isEmpty()) realData = readBanking77Csv(System.getProperty("user.dir")
                + File.separator + "src" + File.separator + "main" + File.separator + "resources"
                + File.separator + "training" + File.separator + "train.csv");

        if (!realData.isEmpty()) {
            System.out.println("--> Success! Loaded " + realData.size() + " rows from CSV.");
            allRows.addAll(realData);
        } else {
            System.out.println("--> CSV not found. Using mock support data.");
            allRows.addAll(getMockBanking77Data());
        }

        // Shuffle to mix support and payment intents
        Collections.shuffle(allRows);

        // MLX requires a separate validation file. We split 90/10.
        int splitIndex = (int) (allRows.size() * 0.9);
        List<TrainingEntry> trainData = allRows.subList(0, splitIndex);
        List<TrainingEntry> validData = allRows.subList(splitIndex, allRows.size());

        System.out.println("Step 3: Writing MLX-ready JSONL files...");
        writeMlxJsonl(trainData, System.getProperty("user.dir")
                + File.separator + "src" + File.separator + "main" + File.separator + "resources"
                + File.separator + "train.jsonl");
        writeMlxJsonl(validData, System.getProperty("user.dir")
                + File.separator + "src" + File.separator + "main" + File.separator + "resources"
                + File.separator + "valid.jsonl");
    }

    // --- 1. MLX / GEMMA 2 FORMATTER (THE NEW PART) ---
    private static void writeMlxJsonl(List<TrainingEntry> rows, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (TrainingEntry row : rows) {
                // We construct the "Chat Template" string manually here.
                // Format: <start_of_turn>user\n{instruction}\nInput:\n{input}<end_of_turn>\n
                // <start_of_turn>model\n{output}<end_of_turn>

                String userContent = row.instruction + "\\nInput:\\n" + escapeJson(row.input);
                String modelContent = escapeJson(row.output);

                String gemmaPrompt = String.format(
                        "<start_of_turn>user\\n%s<end_of_turn>\\n<start_of_turn>model\\n%s<end_of_turn>",
                        userContent, modelContent
                );

                // Wrap it in a JSON object: {"text": "..."}
                String line = String.format("{\"text\": \"%s\"}", gemmaPrompt);

                writer.write(line);
                writer.newLine();
            }
            System.out.println("Success! Wrote " + rows.size() + " lines to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // --- 2. SYNTHETIC GENERATOR (SAME AS BEFORE) ---
    private static List<TrainingEntry> generatePaymentExamples(int count) {
        List<TrainingEntry> samples = new ArrayList<>();
        Random rand = new Random();

        String[] fiat = {"USD", "EUR", "GBP", "bucks"};
        String[] crypto = {"BTC", "ETH", "SOL", "USDC", "USDT", "MATIC", "BNB"};
        String[] recipients = {"Mom", "Dad", "John", "Alice", "Brother", "Sister", "Landlord", "Mike", "Sarah", "Boss"};

        String[][] templates = {
                {"Send {amount} {currency} to {recipient}", "TRANSFER"},
                {"Transfer {amount} {currency} to {recipient}", "TRANSFER"},
                {"Shoot {amount} bucks to {recipient}", "TRANSFER"},
                {"Pay {recipient} {amount} {currency}", "TRANSFER"},
                {"Deposit {amount} {currency}", "DEPOSIT"},
                {"Add {amount} {currency} to my wallet", "DEPOSIT"},
                {"Load {amount} into my account", "DEPOSIT"},
                {"Withdraw {amount} {currency} to my bank", "WITHDRAW"},
                {"Cash out {amount} {currency}", "WITHDRAW"},
                {"Buy {amount} {currency}", "BUY"},
                {"Purchase {amount} {currency}", "BUY"},
                {"Swap USDT for {amount} {currency}", "BUY"},
                {"Sell {amount} {currency}", "SELL"},
                {"Sell {amount} {currency} for cash", "SELL"}
        };

        for (int i = 0; i < count; i++) {
            String[] templateObj = templates[rand.nextInt(templates.length)];
            String templateText = templateObj[0];
            String intent = templateObj[1];

            int amount = rand.nextInt(990) + 10;
            String currency;
            if (intent.equals("BUY") || intent.equals("SELL")) {
                currency = crypto[rand.nextInt(crypto.length)];
            } else {
                currency = rand.nextBoolean() ? fiat[rand.nextInt(fiat.length)] : crypto[rand.nextInt(crypto.length)];
            }
            String recipient = recipients[rand.nextInt(recipients.length)];

            String inputText = templateText
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{currency}", currency)
                    .replace("{recipient}", recipient);

            String cleanCurrency = currency.equals("bucks") ? "USD" : currency;

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"intent\": \"" + intent + "\", ");
            jsonBuilder.append("\"amount\": " + amount + ", ");
            jsonBuilder.append("\"currency\": \"" + cleanCurrency + "\"");
            if (intent.equals("TRANSFER")) jsonBuilder.append(", \"recipient\": \"" + recipient + "\"");
            jsonBuilder.append("}");

            samples.add(new TrainingEntry(
                    "Classify the user query into a payment command JSON.",
                    inputText,
                    jsonBuilder.toString()
            ));
        }
        return samples;
    }

    // --- 3. CSV PARSER (SAME AS BEFORE) ---
    private static List<TrainingEntry> readBanking77Csv(String filePath) {
        List<TrainingEntry> samples = new ArrayList<>();
        Pattern csvPattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                lineCount++;
                if (lineCount == 1) continue;
                String[] columns = csvPattern.split(line);
                if (columns.length >= 2) {
                    String text = cleanCsvField(columns[0]);
                    String label = cleanCsvField(columns[1]);
                    if (BANKING77_MAP.containsKey(label)) {
                        String myIntent = BANKING77_MAP.get(label);
                        String jsonOutput = "{\"intent\": \"" + myIntent + "\"}";
                        samples.add(new TrainingEntry("Classify the user query into a payment command JSON.", text, jsonOutput));
                    }
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return samples;
    }

    private static String cleanCsvField(String field) {
        field = field.trim();
        if (field.startsWith("\"") && field.endsWith("\"")) return field.substring(1, field.length() - 1);
        return field.replace("\"\"", "\"");
    }

    private static List<TrainingEntry> getMockBanking77Data() {
        List<TrainingEntry> s = new ArrayList<>();
        s.add(new TrainingEntry("Classify...", "My card is lost", "{\"intent\": \"FREEZE_CARD\"}"));
        return s;
    }

    static class TrainingEntry {
        String instruction, input, output;

        public TrainingEntry(String i, String in, String out) {
            instruction = i;
            input = in;
            output = out;
        }
    }
}