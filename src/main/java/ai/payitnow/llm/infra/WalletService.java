package ai.payitnow.llm.infra;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;

public class WalletService {

    private static final String DB_URI = "mongodb://localhost:27017";
    private static final String WALLET_DIR = "./user_wallets";

    // Connect to MongoDB
    private final MongoCollection<Document> walletCollection;

    public WalletService() {
        MongoClient mongoClient = MongoClients.create(DB_URI);
        this.walletCollection = mongoClient.getDatabase("payitnow").getCollection("wallets");
        new File(WALLET_DIR).mkdirs();
    }

    /**
     * Gets an existing wallet or creates a new one for the Telegram User ID.
     */
    public Credentials getOrCreateWallet(long userId) throws Exception {
        // 1. Check MongoDB
        Document doc = walletCollection.find(Filters.eq("user_id", userId)).first();

        if (doc != null) {
            // Load existing
            String fileName = doc.getString("wallet_file");
            // In prod, use a secure KMS for the password. Here we use a user-specific salt.
            String password = "PWD_" + userId;
            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
        } else {
            // 2. Create New
            String password = "PWD_" + userId;
            String fileName = WalletUtils.generateNewWalletFile(password, new File(WALLET_DIR));

            // Save metadata to MongoDB
            Document newWallet = new Document("user_id", userId)
                    .append("wallet_file", fileName)
                    .append("chain", "ETH_BSC_ARC") // Same address works for all EVM chains
                    .append("created_at", System.currentTimeMillis());

            walletCollection.insertOne(newWallet);

            return WalletUtils.loadCredentials(password, new File(WALLET_DIR + "/" + fileName));
        }
    }
}