package ai.payitnow.llm.circle;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CirclePayoutService {
    // TODO: these are for wallet and contracts...
    //TEST_API_KEY:46819dec11c7ea4d3a4e06b0509e4442:d6fc12cb8fd58b37436ab976a2de3e25
    //"TEST_API_KEY:63b85833d20e1507b5ee3fd664c40df9:df4cafebf9fcb7153ddc1e34efdefda5";

    private static final String API_KEY = "SAND_API_KEY:11b0ed574987c7a528df3a84dec80777:80877a7fcd0951b3061d5b807919ea9f";
    private static final String BASE_URL = "https://api-sandbox.circle.com/v1";

    private final OkHttpClient client = new OkHttpClient();

    // TOGGLE: Set to false only if you have a verified Circle Mint Institutional Account
    private static final boolean MOCK_MODE = false;
    
    /**
     * MASTER METHOD: Create Beneficiary -> Send Payout
     * Create a Payout (USDC -> Fiat Bank Wire)
     */
    public String sendFiatToPerson(String name, String email, String country, String amount, String currency) {
        if (MOCK_MODE) {
            System.out.println("🏦 [MOCK BANK] Initiating Wire Transfer...");
            System.out.println("   To: " + name + " (" + email + ")");
            System.out.println("   Bank Location: " + country);
            System.out.println("   Amount: " + amount + " " + currency);

            try {
                // Simulate Bank Processing Delay
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Return a fake Payout ID (looks real)
            return "payout_" + UUID.randomUUID().toString().substring(0, 18);
        } else {
            try {
                // 1. Create (or Find) Beneficiary
                // In a real app, check DB first. For MVP, we create a new one each time.
                String beneficiaryId = createWireBeneficiary(name, email, country, currency);

                if (beneficiaryId.startsWith("Error")) return beneficiaryId;

                // 2. Execute Payout
                return createPayout(beneficiaryId, amount, currency);

            } catch (Exception e) {
                return "Exception: " + e.getMessage();
            }
        }
    }

    private String createWireBeneficiary(String name, String email, String country, String currency) throws Exception {
        JSONObject json = new JSONObject();
        json.put("idempotencyKey", UUID.randomUUID().toString());

        // 1. ACCOUNT DETAILS (Root Level - No Wrapper)
        // For Mexico: Account Number = CLABE (18 digits)
        // For Mexico: Routing Number = SWIFT BIC
        json.put("accountNumber", "123456789012345678"); // Mock CLABE
        // SWIFT/BIC for Mexico (Not ABA!)
        // In a real app, you'd look this up based on the user's bank selection.
        //json.put("routingNumber", "BBVAMXMM"); // Mock SWIFT for BBVA Mexico
        // FIX: Change 'BBVAMXMM' to 'BCMRMXMMXXX'
        // BCMR is the correct Bank Code for BBVA Bancomer in the SWIFT system.
        // MX is the Country.
        // MM is the Location.
        // XXX is the Head Office indicator.
        json.put("routingNumber", "BCMRMXMMXXX");

        // 2. BILLING DETAILS (The Owner)
        JSONObject billing = new JSONObject();
        billing.put("name", name);
        billing.put("city", "Mexico City");
        billing.put("country", country); // "MX"
        billing.put("line1", "Av Reforma 123");
        billing.put("postalCode", "06500");
        // district/region is optional but good for address validation
        billing.put("district", "CDMX");
        json.put("billingDetails", billing);

        // 3. BANK ADDRESS (The Branch)
        JSONObject bankAddr = new JSONObject();
        bankAddr.put("bankName", "BBVA MEXICO S.A."); // Use the formal SWIFT name
        bankAddr.put("city", "Mexico City");
        bankAddr.put("country", country); // "MX"
        json.put("bankAddress", bankAddr);

        // NOTE: 'email' is NOT sent to this endpoint. You store it locally in your DB.

        Request request = new Request.Builder()
                .url(BASE_URL + "/businessAccount/banks/wires") // The correct endpoint
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + API_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            JSONObject res = new JSONObject(body);

            if (!response.isSuccessful()) {
                // Return detailed error for debugging
                return "Error creating beneficiary: " + body;
            }
            return res.getJSONObject("data").getString("id");
        }
    }

    private String createPayout(String beneficiaryId, String amount, String currency) throws Exception {
        JSONObject json = new JSONObject();
        json.put("idempotencyKey", UUID.randomUUID().toString());

        // 1. DESTINATION (Nested Object)
        JSONObject dest = new JSONObject();
        dest.put("type", "wire");
        dest.put("id", beneficiaryId); // The ID you got from /businessAccount/banks/wires
        json.put("destination", dest);

        // 2. AMOUNT (Nested Object)
        JSONObject amtObj = new JSONObject();
        amtObj.put("amount", amount); // Must be a string like "500.00"
        amtObj.put("currency", currency);
        json.put("amount", amtObj);

        // 3. SOURCE (Optional but recommended)
        JSONObject src = new JSONObject();
        src.put("type", "wallet");
        src.put("id", "1017370587");//masterWalletId - curl -H 'Accept: application/json' -H "Authorization: Bearer SAND_API_KEY:11b0ed574987c7a528df3a84dec80777:80877a7fcd0951b3061d5b807919ea9f" -X GET --url https://api-sandbox.circle.com/v1/configuration
        json.put("source", src);

        Request request = new Request.Builder()
                .url(BASE_URL + "/businessAccount/payouts") // Correct Payout endpoint
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + API_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            JSONObject res = new JSONObject(body);

            if (!response.isSuccessful()) {
                // This will now print the 'errors' array if parameter invalid persists
                return "Error payout: " + body;
            }
            return res.getJSONObject("data").getString("id");
        }
    }

    /**
     * Helper: Fetch the Master Wallet ID automatically.
     * Call this once on startup to configure the service.
     */
    public String getMasterWalletId() {
        Request request = new Request.Builder()
                .url(BASE_URL + "/configuration")
                .get()
                .addHeader("Authorization", "Bearer " + API_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;

            JSONObject json = new JSONObject(response.body().string());
            // Path: data -> payments -> masterWalletId
            return json.getJSONObject("data")
                    .getJSONObject("payments")
                    .getString("masterWalletId");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigDecimal getMasterWalletBalance() {
        Request request = new Request.Builder()
                .url(BASE_URL + "/businessAccount/wallets")
                .get()
                .addHeader("Authorization", "Bearer " + API_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            JSONObject json = new JSONObject(response.body().string());
            // Circle returns an array of balances (USDC, etc.)
            JSONArray balances = json.getJSONObject("data").getJSONArray("balances");
            for (int i = 0; i < balances.length(); i++) {
                JSONObject b = balances.getJSONObject(i);
                if (b.getString("currency").equals("USD")) {
                    return new BigDecimal(b.getString("amount"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

}