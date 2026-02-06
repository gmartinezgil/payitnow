package ai.payitnow.llm.circle;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CctpService {

    // 1. CONFIGURATION (Testnet)
    private final Web3j arcNode = Web3j.build(new HttpService("https://rpc.testnet.arc.network"));
    private final Web3j ethNode = Web3j.build(new HttpService("https://sepolia.infura.io/v3/c7b69a99e891405aaceac7ae7e0bc56d"));

    // 1. V2 CONTRACT ADDRESSES (Testnet) [Updated from Circle Docs]
    // Arc Testnet TokenMessenger V2
    private static final String ARC_TOKEN_MESSENGER = "0x8FE6B999Dc680CcFDD5Bf7EB0974218be2542DAA";
    // Sepolia TokenMessenger V2
    private static final String ETH_TOKEN_MESSENGER = "0x9f3B8679c73C2Fef8b59B4f3444d4e156fb70AA5";
    // MessageTransmitter V2 (for receiving on Eth)
    private static final String ETH_MESSAGE_TRANSMITTER = "0x7865fAfC2db2093669d92c0F33AeEF291086BEFD";
    // Contract Addresses (Testnet)
    private static final String USDC_ARC = "0x3600000000000000000000000000000000000000"; // Arc USDC

    // Circle Iris API (Attestation Service)
    private static final String IRIS_API_URL = "https://iris-api-sandbox.circle.com/attestations";

    // DESTINATION DOMAINS: 0=Eth, 1=Avalanche, 2=OP, 3=Arb, 6=Base
    private static final BigInteger DOMAIN_ETH = BigInteger.ZERO;

    /**
     * STEP 1: Burn USDC on Arc (V2 Signature)
     */
    public String burnUSDC(Credentials wallet, BigInteger amount, String destWalletAddr) {
        try {
            // V2 requires 7 Arguments:
            // 1. amount (uint256)
            // 2. destinationDomain (uint32)
            // 3. mintRecipient (bytes32)
            // 4. burnToken (address)
            // 5. destinationCaller (bytes32) -> NEW: 0x0 means "anyone can mint"
            // 6. hookData (bytes) -> NEW: Empty for standard transfer
            // Note: Some V2 implementations split this. Standard V2 depositForBurn signature:
            // depositForBurn(uint256, uint32, bytes32, address) NO LONGER EXISTS alone.
            // We use: depositForBurn(uint256, uint32, bytes32, address) is DEPRECATED.
            // CORRECT V2: depositForBurn(uint256, uint32, bytes32, address, bytes32, uint256, uint32)

            byte[] recipientBytes = Numeric.hexStringToByteArray(padAddress(destWalletAddr));
            byte[] emptyBytes32 = new byte[32]; // 0x000...

            Function function = new Function(
                    "depositForBurn",
                    Arrays.asList(
                            new Uint256(amount),                // amount
                            new Uint32(DOMAIN_ETH),             // destinationDomain
                            new Bytes32(recipientBytes),        // mintRecipient
                            new Address(USDC_ARC),              // burnToken
                            new Bytes32(emptyBytes32),          // destinationCaller (Optional)
                            new Uint256(BigInteger.ZERO),       // maxFee (0 for Standard)
                            new Uint32(BigInteger.ZERO)         // minFinalityThreshold (0 for Default)
                    ),
                    Collections.emptyList()
            );

            return sendRawTransaction(arcNode, wallet, ARC_TOKEN_MESSENGER, function);

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * STEP 2: Get Attestation from Circle Iris
     * We need the "Message Bytes" from the logs to ask Circle for a signature.
     */
    public String fetchAttestation(String burnTxHash) {
        try {
            // 1. Get Log from Transaction Receipt
            EthGetTransactionReceipt receipt = arcNode.ethGetTransactionReceipt(burnTxHash).send();
            if (!receipt.getTransactionReceipt().isPresent()) return "Pending";

            String messageBytes = extractMessageBytes(receipt.getTransactionReceipt().get().getLogs());
            if (messageBytes == null) return "No Message Found";

            // 2. Poll Circle Iris API
            String messageHash = org.web3j.crypto.Hash.sha3(messageBytes);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(IRIS_API_URL + "/" + messageHash)
                    .get()
                    .build();

            // Retry loop (It takes ~15 seconds for Iris to sign)
            for (int i = 0; i < 10; i++) {
                Response response = client.newCall(request).execute();
                JSONObject json = new JSONObject(response.body().string());

                if (json.has("attestation") && !json.getString("attestation").equals("PENDING")) {
                    return json.getString("attestation");
                }
                Thread.sleep(2000);
            }
            return "Timeout";

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * STEP 3: Mint on Destination (Ethereum)
     */
    public String mintUSDC(Credentials wallet, String messageBytes, String attestationSignature) {
        try {
            // Function: receiveMessage(message, attestation)
            Function function = new Function(
                    "receiveMessage",
                    Arrays.asList(
                            new org.web3j.abi.datatypes.DynamicBytes(Numeric.hexStringToByteArray(messageBytes)),
                            new org.web3j.abi.datatypes.DynamicBytes(Numeric.hexStringToByteArray(attestationSignature))
                    ),
                    Collections.emptyList()
            );

            // Execute on Ethereum
            return sendRawTransaction(ethNode, wallet, ETH_MESSAGE_TRANSMITTER, function);

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // --- Helpers ---
    private String padAddress(String address) {
        if (address.startsWith("0x")) address = address.substring(2);
        return String.format("%64s", address).replace(' ', '0');
    }

    private String sendRawTransaction(Web3j client, Credentials wallet, String contract, Function function) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);
        BigInteger nonce = client.ethGetTransactionCount(wallet.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
        BigInteger gasPrice = client.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(200000);

        RawTransaction rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contract, BigInteger.ZERO, encodedFunction);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTx, wallet);
        return client.ethSendRawTransaction(Numeric.toHexString(signedMessage)).send().getTransactionHash();
    }

    /**
     * STEP 2b: Extract "Message Bytes" from Transaction Logs
     * This is the raw data Circle needs to sign.
     */
    private String extractMessageBytes(List<Log> logs) {
        // 1. Define the Event: event MessageSent(bytes message)
        Event messageSentEvent = new Event("MessageSent",
                Arrays.asList(new TypeReference<DynamicBytes>() {
                }));

        String eventSignature = EventEncoder.encode(messageSentEvent);

        for (Log log : logs) {
            List<String> topics = log.getTopics();

            // 2. Match the Event Signature (Topic 0)
            if (topics != null && !topics.isEmpty() && topics.get(0).equals(eventSignature)) {

                // 3. Decode the Data (The 'message' is non-indexed, so it's in the data field)
                List<Type> results = FunctionReturnDecoder.decode(
                        log.getData(),
                        messageSentEvent.getNonIndexedParameters()
                );

                if (!results.isEmpty()) {
                    // Return the hex string of the message bytes
                    byte[] bytes = (byte[]) results.get(0).getValue();
                    return Numeric.toHexString(bytes);
                }
            }
        }
        return null;
    }

}