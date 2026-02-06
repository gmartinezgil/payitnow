package ai.payitnow.llm.infra;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class CryptoService {

    // RPC Endpoints (Replace with your Infura/Alchemy/QuickNode keys)
    // TESTNET
    private final Web3j ethNode = Web3j.build(new HttpService("https://sepolia.infura.io/v3/c7b69a99e891405aaceac7ae7e0bc56d"));
    private final Web3j bscNode = Web3j.build(new HttpService("https://bsc-testnet-dataseed.bnbchain.org"));
    private final Web3j arcNode = Web3j.build(new HttpService("https://rpc.testnet.arc.network")); // Arc Testnet

    // 2. TOKEN CONTRACT ADDRESSES (Testnet)
    // Sepolia USDC Address (Circle Official Testnet Deployment)
    private static final String USDC_SEPOLIA = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238";
    // BSC Testnet USDC (Placeholder - Verify your specific faucet token address!)
    private static final String USDC_BSC = "0x64544969ed7EBf5f083679233325356EbE738930";

    // 3. ERC-20 FUNCTION SIGNATURE: balanceOf(address) -> 0x70a08231
    private static final String BALANCE_OF_METHOD = "0x70a08231000000000000000000000000";

//    /**
//     * Checks balance on the requested chain.
//     */
//    public String checkBalance(String address, String currency) throws Exception {
//        Web3j targetNode;
//
//        // Select Network based on currency
//        if (currency.equalsIgnoreCase("ETH")) targetNode = ethNode;
//        else if (currency.equalsIgnoreCase("BNB")) targetNode = bscNode;
//        else if (currency.equalsIgnoreCase("USDC")) targetNode = arcNode; // Arc uses native USDC
//        else targetNode = ethNode; // Default
//
//        // Get Native Balance (Gas Token)
//        BigInteger wei = targetNode.ethGetBalance(address, DefaultBlockParameterName.LATEST)
//                .send().getBalance();
//
//        BigDecimal amount = Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
//        return amount.toPlainString();
//    }

//    public String checkBalance(String walletAddress, String currency) throws Exception {
//        // CLEANUP: Remove "bucks" or other aliases
//        String symbol = currency.toUpperCase().replace("BUCKS", "USDC");
//
//        // A. ARC NETWORK (USDC is Native)
//        if (symbol.equals("USDC")) {
//            // Check Arc first for USDC as it is native there
//            return getNativeBalance(arcNode, walletAddress);
//        }
//
//        // B. ETHEREUM SEPOLIA
//        if (symbol.equals("ETH")) {
//            return getNativeBalance(ethNode, walletAddress);
//        }
//        // Sepolia ERC-20 (USDC on Eth)
//        else if (symbol.equals("USDC_ETH")) {
//            return getTokenBalance(ethNode, USDC_SEPOLIA, walletAddress, 6); // USDC has 6 decimals
//        }
//
//        // C. BINANCE SMART CHAIN
//        if (symbol.equals("BNB")) {
//            return getNativeBalance(bscNode, walletAddress);
//        }
//
//        return "0.00"; // Default fallback
//    }
//
//    // --- HELPER: Get Native Balance (ETH, BNB, Arc-USDC) ---
//    private String getNativeBalance(Web3j client, String address) throws Exception {
//        BigInteger wei = client.ethGetBalance(address, DefaultBlockParameterName.LATEST)
//                .send().getBalance();
//        return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER).toPlainString();
//    }
//
//    // --- HELPER: Get ERC-20 Token Balance (The Missing Piece) ---
//    private String getTokenBalance(Web3j client, String contractAddress, String walletAddress, int decimals) throws Exception {
//        // 1. Construct Data: MethodID + Padding + Address (without 0x)
//        String cleanAddress = walletAddress.startsWith("0x") ? walletAddress.substring(2) : walletAddress;
//        String data = BALANCE_OF_METHOD + cleanAddress;
//
//        // 2. Make the Read-Only Call
//        Transaction call = Transaction.createEthCallTransaction(walletAddress, contractAddress, data);
//        EthCall response = client.ethCall(call, DefaultBlockParameterName.LATEST).send();
//
//        if (response.hasError()) return "Error: " + response.getError().getMessage();
//
//        // 3. Decode Result (Hex -> BigInteger)
//        String hexValue = response.getValue();
//        if (hexValue == null || hexValue.equals("0x")) return "0.0";
//
//        BigInteger rawBalance = new BigInteger(hexValue.substring(2), 16);
//
//        // 4. Format Decimals (e.g. USDC is 6, not 18)
//        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
//        return new BigDecimal(rawBalance).divide(divisor, 2, RoundingMode.HALF_DOWN).toPlainString();
//    }
//public String checkBalance(String walletAddress, String currency) throws Exception {
//    String symbol = currency.toUpperCase().replace("BUCKS", "USDC");
//
//    // A. ARC NETWORK (USDC is Native Gas Token)
//    if (symbol.equals("USDC")) {
//        return getNativeBalance(arcNode, walletAddress);
//    }
//
//    // B. ETHEREUM SEPOLIA (Native ETH)
//    if (symbol.equals("ETH")) {
//        return getNativeBalance(ethNode, walletAddress);
//    }
//    // C. ETHEREUM SEPOLIA (ERC-20 USDC)
//    else if (symbol.equals("USDC_ETH")) {
//        // Call the ERC-20 helper
//        return getTokenBalance(ethNode, USDC_SEPOLIA, walletAddress, 6);
//    }
//
//    // D. BINANCE SMART CHAIN (Native BNB)
//    if (symbol.equals("BNB")) {
//        return getNativeBalance(bscNode, walletAddress);
//    }
//
//    return "0.00";
//}

    public String checkBalance(String address, String currency) {
        try {
            String symbol = currency.toUpperCase();

            // 1. Ethereum Sepolia (Native)
            if (symbol.equals("ETH")) return getNativeBalance(ethNode, address);

            // 2. Arc Network (Native USDC)
            if (symbol.equals("USDC") || symbol.equals("ARC")) return getNativeBalance(arcNode, address);

            // 3. USDC on Sepolia (ERC-20)
            // Note: Ensure USDC_SEPOLIA_ADDR is set to: 0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238
            if (symbol.equals("USDC_ETH") || symbol.equals("SEPOLIA_USDC")) {
                return getTokenBalance(ethNode, USDC_SEPOLIA, address, 6);
            }

            return "0.00";
        } catch (Exception e) {
            return "0.00";
        }
    }

    // Helper: Native Balance (ETH, BNB)
    private String getNativeBalance(Web3j client, String address) throws Exception {
        BigInteger wei = client.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send().getBalance();
        return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER).toPlainString();
    }

    /**
     * Helper: ERC-20 Token Balance via eth_call
     * @param decimals USDC uses 6 decimals, most others use 18.
     */
    private String getTokenBalance(Web3j client, String contractAddress, String walletAddress, int decimals) throws Exception {
        // 1. Construct Data Payload: MethodID + Address (stripped of 0x)
        String cleanAddress = walletAddress.startsWith("0x") ? walletAddress.substring(2) : walletAddress;
        String data = BALANCE_OF_METHOD + cleanAddress;

        // 2. Make Read-Only Call
        Transaction call = Transaction.createEthCallTransaction(walletAddress, contractAddress, data);
        EthCall response = client.ethCall(call, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) return "Error: " + response.getError().getMessage();

        // 3. Decode Result
        String hexValue = response.getValue();
        if (hexValue == null || hexValue.equals("0x")) return "0.0";

        BigInteger rawBalance = new BigInteger(hexValue.substring(2), 16);

        // 4. Format with Decimals
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        return new BigDecimal(rawBalance).divide(divisor, 2, RoundingMode.HALF_DOWN).toPlainString();
    }

    /**
     * Sends Native Token (ETH/BNB/MATIC) to a destination.
     */
    public String sendNativeToken(Credentials wallet, String toAddress, BigDecimal amount, String currency) {
        try {
            Web3j client = null;
            if (currency.equals("ETH")) client = ethNode;
            else if (currency.equals("BNB")) client = bscNode;
            else if (currency.equals("USDC")) client = arcNode; // Arc native

            if (client == null) return "Error: Unsupported chain";

            // 1. Get Nonce
            BigInteger nonce = client.ethGetTransactionCount(wallet.getAddress(), DefaultBlockParameterName.LATEST)
                    .send().getTransactionCount();

            // 2. Prepare Transaction
            BigInteger valueWei = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();
            // Gas Limit (Standard 21000) & Price (Simple/Legacy for MVP)
            BigInteger gasLimit = BigInteger.valueOf(21000);
            BigInteger gasPrice = client.ethGasPrice().send().getGasPrice();

            RawTransaction rawTx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, valueWei);

            // 3. Sign & Send
            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, wallet);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction response = client.ethSendRawTransaction(hexValue).send();

            if (response.hasError()) {
                return "Tx Error: " + response.getError().getMessage();
            }
            return response.getTransactionHash();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

}