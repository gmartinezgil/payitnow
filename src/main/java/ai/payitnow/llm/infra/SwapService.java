package ai.payitnow.llm.infra;

import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SwapService {
    private static final String API_KEY = "b39b4b62ff7f4740b9cceb88991bff93";
    private static final String SECRET = "782596d7361c28c432d4fd605d8ed352cfbcaea21077c7d285b0de721ddcfb2b";
    private static final String BASE_URL = "https://api.changelly.com/v2";

    private final OkHttpClient client = new OkHttpClient();

    public String getExchangeRate(String from, String to, String amount) {
        try {
            // JSON Payload
            String json = String.format(
                    "{\"jsonrpc\": \"2.0\", \"method\": \"getExchangeAmount\", \"params\": [{\"from\": \"%s\", \"to\": \"%s\", \"amount\": \"%s\"}], \"id\": 1}",
                    from.toLowerCase(), to.toLowerCase(), amount
            );

            // Sign Request (HMAC-SHA512)
            Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secret_key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA512");
            sha512_HMAC.init(secret_key);
            String signature = toHex(sha512_HMAC.doFinal(json.getBytes()));

            // Send Request
            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .addHeader("X-Api-Key", API_KEY)
                    .addHeader("X-Api-Signature", signature)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.body().string(); // Returns JSON with estimated amount
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error calculating rate.";
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}