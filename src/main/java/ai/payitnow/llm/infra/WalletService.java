package ai.payitnow.llm.infra;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;

//public class WalletService {
//
//    private static final String DB_URI = "mongodb://localhost:27017";
//    private static final String WALLET_DIR = "./user_wallets";
//
//    // Connect to MongoDB
//    private final MongoCollection<Document> walletCollection;
//
//    public WalletService() {
//        MongoClient mongoClient = MongoClients.create(DB_URI);
//        this.walletCollection = mongoClient.getDatabase("payitnow").getCollection("wallets");
//        new File(WALLET_DIR).mkdirs();
//    }
//
//    /**
//     * Gets an existing wallet or creates a new one for the Telegram User ID.
//     */
//    public Credentials getOrCreateWallet(long userId) throws Exception {
//        // 1. Check MongoDB
//        Document doc = walletCollection.find(Filters.eq("user_id", userId)).first();
//
//        if (doc != null) {
//            // Load existing
//            String fileName = doc.getString("wallet_file");
//            // In prod, use a secure KMS for the password. Here we use a user-specific salt.
//            String password = "PWD_" + userId;
//            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
//        } else {
//            // 2. Create New
//            String password = "PWD_" + userId;
//            String fileName = WalletUtils.generateNewWalletFile(password, new File(WALLET_DIR));
//
//            // Save metadata to MongoDB
//            Document newWallet = new Document("user_id", userId)
//                    .append("wallet_file", fileName)
//                    .append("chain", "ETH_BSC_ARC") // Same address works for all EVM chains
//                    .append("created_at", System.currentTimeMillis());
//
//            walletCollection.insertOne(newWallet);
//
//            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
//        }
//    }
//}
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase; // Import this
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;

public class WalletService {

    private static final String DB_URI = "mongodb://localhost:27017";
    private static final String WALLET_DIR = "./user_wallets";

    // 1. Promote 'database' to a class field so we can expose it
    private final MongoDatabase database;
    private final MongoCollection<Document> walletCollection;

    public WalletService() {
        // Initialize Connection
        MongoClient mongoClient = MongoClients.create(DB_URI);

        // 2. Assign the database to the field
        this.database = mongoClient.getDatabase("payitnow");

        // 3. Get the specific collection for wallets
        this.walletCollection = database.getCollection("wallets");

        // Ensure directory exists
        new File(WALLET_DIR).mkdirs();
    }

    /**
     * NEW: Expose the database object.
     * This allows other services (like TransactionMonitor) to access
     * their own collections (e.g. "active_swaps") using the same connection.
     */
    public MongoDatabase getDatabase() {
        return this.database;
    }

    /**
     * Gets an existing wallet or creates a new one for the Telegram User ID.
     */
    public Credentials getOrCreateWallet(long userId) throws Exception {
        // ... (Existing implementation remains the same) ...
        Document doc = walletCollection.find(Filters.eq("user_id", userId)).first();

        if (doc != null) {
            String fileName = doc.getString("wallet_file");
            String password = "PWD_" + userId;
            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
        } else {
            String password = "PWD_" + userId;
            String fileName = WalletUtils.generateNewWalletFile(password, new File(WALLET_DIR));

            Document newWallet = new Document("user_id", userId)
                    .append("wallet_file", fileName)
                    .append("chain", "ETH_BSC_ARC")
                    .append("created_at", System.currentTimeMillis());

            walletCollection.insertOne(newWallet);
            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
        }
    }

    public Document getContact(long userId, String nickname) {
        // Search for a contact belonging to this user with this nickname
        return getDatabase().getCollection("contacts")
                .find(Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.eq("nickname", nickname.toLowerCase())
                )).first();
    }

}