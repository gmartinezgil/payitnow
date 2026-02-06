package ai.payitnow.llm.infra;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SwapService {

    // 1. CONFIGURATION
    // Replace with the specific endpoint from https://api-doc.letsexchange.io/
    private static final String BASE_URL = "https://api.letsexchange.io";
    private static final String API_KEY = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0b2tlbiIsImRhdGEiOnsiaWQiOjE1MDQsImhhc2giOiJleUpwZGlJNkltcEpOR2hGZWtOWVRXUmtkVGhLVG1Sa1VubFdPSGM5UFNJc0luWmhiSFZsSWpvaVQwdDFkbmhvYmxoeGJsd3ZiMFJJTTNaNVRWUjJVWGRTZUdGelVGSnVXRUpuVGxOalJtcDZUVFp2TlcxVmN6aFZVa3MxVWpsR2FGazJjR2xZU0ZsaWVtMDVPVVlyWXl0Qk5qaHBWM0ZxYjFFMVVFd3hTa1ZwYzBaUWJuZEdiVGRFTkU1RU4zTkRNVk5hWEM5NlNUMGlMQ0p0WVdNaU9pSTVNVFU0WmpZeE5EZzVNVGRrTTJFMFlUSmxPR1F4T0dFNU9EWTBZalkyTlRrMVpHWTVZMlV3T0RjNE1HVTFPVFptWXpkaFpXRTBaR1psWkRnd05USXhJbjA9In0sImlzcyI6Imh0dHBzOlwvXC9hcGkubGV0c2V4Y2hhbmdlLmlvXC9hcGlcL3YxXC9hcGkta2V5IiwiaWF0IjoxNzcwMTQyNzYwLCJleHAiOjIwOTE1NTA3NjAsIm5iZiI6MTc3MDE0Mjc2MCwianRpIjoiSzY5dkJIRFpYSkZsVG9FWSJ9.0VvbWBuRTJ4ewni_kbxyn8jw7NgY8xz2SUN-Ja5GlUY"; // Your Bearer Token

    // PRIORITY: Prefer these networks if a coin is available on multiple chains
    private static final List<String> NETWORK_PRIORITY = Arrays.asList("ERC20", "ETH", "TRC20", "BSC", "BEP20", "POLYGON", "SOL");

    // CACHE: Stores Coin Ticker -> List of Available Networks (e.g., "USDC" -> ["ETH", "TRC20", "SOL"])
    private final Map<String, List<String>> networkCache = new ConcurrentHashMap<>();

    private final OkHttpClient client = new OkHttpClient();

    /**
     * Inner Class to hold Quote Info (Amount + Limits)
     */
    public static class Quote {
        public String estimatedAmount;
        public String minAmount;
        public String error;

        public Quote(String est, String min, String err) {
            this.estimatedAmount = est;
            this.minAmount = min;
            this.error = err;
        }

        @Override
        public String toString() {
            return "Quote{" +
                    "estimatedAmount='" + estimatedAmount + '\'' +
                    ", minAmount='" + minAmount + '\'' +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

//    /**
//     * STEP 1: Get Valid Networks for a Coin (v2/coins)
//     * We need this to know if we should send "ERC20", "TRC20", or "ETH" as the network.
//     */
//    public String getCorrectNetwork(String coinTicker) {
//        // Correct Endpoint: https://api.letsexchange.io/api/v2/coins
//        String url = BASE_URL + "/api/v2/coins";
//
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .addHeader("Authorization", "Bearer " + API_KEY)
//                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (!response.isSuccessful()) return "ETH";
//
//            String jsonData = response.body().string();
//            JSONArray coins = new JSONArray(jsonData);
//
//            for (int i = 0; i < coins.length(); i++) {
//                JSONObject coin = coins.getJSONObject(i);
//                if (coin.getString("code").equalsIgnoreCase(coinTicker)) {
//                    JSONArray networks = coin.getJSONArray("networks");
//                    if (networks.length() > 0) {
//                        // FIX 2: Parse Object, not String
//                        // The API returns [{ "code": "ERC20", ... }, ...]
//                        JSONObject netObj = networks.getJSONObject(0);
//                        return netObj.getString("code");
//                    }
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("⚠️ Network lookup failed: " + e.getMessage());
//        }
//        return "ETH"; // Fallback
//    }

    /**
     * SMART NETWORK SELECTOR (Cached & Dynamic)
     * 1. Checks Cache.
     * 2. If missing, calls API v2/coins to get REAL supported networks.
     * 3. Picks the best network from PRIORITY list.
     */
    public String getCorrectNetwork(String coinTicker) {
        String ticker = coinTicker.toUpperCase();

        // 1. CHECK CACHE
        if (networkCache.containsKey(ticker)) {
            return selectBestNetwork(networkCache.get(ticker));
        }

        // 2. FETCH FROM API (If not in cache)
        System.out.println("🌍 Fetching valid networks for: " + ticker);
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v2/coins")
                .get()
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "ETH"; // Safe Fallback

            JSONArray coins = new JSONArray(response.body().string());
            for (int i = 0; i < coins.length(); i++) {
                JSONObject coin = coins.getJSONObject(i);

                // Found the coin?
                if (coin.getString("code").equalsIgnoreCase(ticker)) {
                    JSONArray networksJson = coin.getJSONArray("networks");
                    List<String> validNetworks = new ArrayList<>();

                    for (int j = 0; j < networksJson.length(); j++) {
                        validNetworks.add(networksJson.getJSONObject(j).getString("code"));
                    }

                    // Save to Cache
                    networkCache.put(ticker, validNetworks);

                    // Select Best
                    return selectBestNetwork(validNetworks);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Network lookup failed: " + e.getMessage());
        }

        return "ETH"; // Default fallback
    }

    /**
     * Helper: Picks the best network from the available list based on our Priority.
     */
    private String selectBestNetwork(List<String> availableNetworks) {
        // 1. Look for Priority match (e.g., prefer ERC20/ETH over SOL for USDC)
        for (String priority : NETWORK_PRIORITY) {
            for (String net : availableNetworks) {
                if (net.equalsIgnoreCase(priority)) return net;
            }
        }
        // 2. Fallback: Return the first available one (e.g., if only "ALGO" is available)
        return availableNetworks.isEmpty() ? "ETH" : availableNetworks.get(0);
    }

    /**
     * GET QUOTE (With Min/Max checks)
     */
    public Quote getQuote(String fromCoin, String toCoin, String amount) {
        try {
            // Now this calls the Dynamic method, not the hardcoded one!
            String netFrom = getCorrectNetwork(fromCoin);
            String netTo = getCorrectNetwork(toCoin);

            System.out.println("🔄 Quoting " + fromCoin + " (" + netFrom + ") -> " + toCoin + " (" + netTo + ")");

            JSONObject json = new JSONObject();
            json.put("from", fromCoin.toUpperCase());
            json.put("to", toCoin.toUpperCase());
            json.put("network_from", netFrom);
            json.put("network_to", netTo);
            json.put("amount", Double.parseDouble(amount));
            json.put("float", true);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/info")
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body().string();
                if (!response.isSuccessful()) {
                    return new Quote(null, null, "API Error " + response.code() + ": " + respBody);
                }

                JSONObject result = new JSONObject(respBody);
                String est = result.optString("amount", "0");
                String min = result.optString("min_amount", "0");

                return new Quote(est, min, null);
            }
        } catch (Exception e) {
            return new Quote(null, null, "Exception: " + e.getMessage());
        }
    }

    public String getExchangeRate(String fromCoin, String toCoin, String amount) {
        try {
            // 1. Get Networks
            String networkFrom = getCorrectNetwork(fromCoin);
            String networkTo = getCorrectNetwork(toCoin);

            System.out.println("🔄 Quoting " + fromCoin + " (" + networkFrom + ") -> " + toCoin + " (" + networkTo + ")");

            // 2. Build Payload
            JSONObject json = new JSONObject();
            json.put("from", fromCoin.toUpperCase());
            json.put("to", toCoin.toUpperCase());
            json.put("network_from", networkFrom);
            json.put("network_to", networkTo);
            json.put("amount", Double.parseDouble(amount));
            json.put("float", true);

            // 3. Execute Request
            // FIX 3: Correct Endpoint: https://api.letsexchange.io/api/v1/info
            // If the previous one failed, it might be the host structure.
            // We stick to the standard documented path.
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/info")
                    .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();

                if (!response.isSuccessful()) {
                    return "API Error: " + response.code() + " - " + responseBody;
                }

                JSONObject result = new JSONObject(responseBody);
                if (result.has("amount")) {
                    return result.get("amount").toString();
                } else {
                    return "No quote found in response";
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * STEP 3: Create Transaction (Robust Debug Version)
     * Returns the Deposit Address where the user must send funds.
     */
    public JSONObject createTransaction(String fromCoin, String toCoin, String amount, String userWallet) {
        try {
            String netFrom = getCorrectNetwork(fromCoin);
            String netTo = getCorrectNetwork(toCoin);

            System.out.println("🚀 CREATING TX: " + fromCoin + "(" + netFrom + ") -> " + toCoin + "(" + netTo + ") | Amount: " + amount);

            JSONObject json = new JSONObject();
            json.put("coin_from", fromCoin.toUpperCase());
            json.put("coin_to", toCoin.toUpperCase());
            json.put("network_from", netFrom);
            json.put("network_to", netTo);
            json.put("deposit_amount", Double.parseDouble(amount));
            json.put("withdrawal", userWallet);
            json.put("return", userWallet);
            json.put("float", true);

            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/v1/transaction")
                    .post(body)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body().string();

                // Debugging: If it's still HTML, print it to see why
                if (respBody.trim().startsWith("<")) {
                    System.out.println("⚠️ SERVER RETURNED HTML: " + respBody);
                    return new JSONObject().put("error", "Server returned HTML (Blocked/404)");
                }

                if (!response.isSuccessful()) {
                    return new JSONObject().put("error", "API Error " + response.code() + ": " + respBody);
                }

                return new JSONObject(respBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject().put("error", "Exception: " + e.getMessage());
        }
    }

    /**
     * NEW: Check Status (GET /api/v1/transaction/{id})
     */
    public String getTransactionStatus(String txId) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v1/transaction/" + txId)
                .get()
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return "error";

            JSONObject json = new JSONObject(response.body().string());
            // Common statuses: "wait", "confirmation", "exchange", "sending", "success", "overdue", "refund"
            return json.optString("status", "unknown");
        } catch (Exception e) {
            return "error";
        }
    }

}