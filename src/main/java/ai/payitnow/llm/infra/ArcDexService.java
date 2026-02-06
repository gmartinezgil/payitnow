package ai.payitnow.llm.infra;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

public class ArcDexService {

    // 1. Arc Testnet Config
    private final Web3j arcNode = Web3j.build(new HttpService("https://rpc.testnet.arc.network"));

    // 2. DEX Router Address (Placeholder: Replace with real Uniswap/Sushi fork on Arc)
    private static final String ARC_DEX_ROUTER = "0xC532a74256D3Db42D0Bf7a0400fEFDbad7694008";
    private static final String USDC_ADDRESS = "0x3600000000000000000000000000000000000000"; // Arc Native USDC

    /**
     * Fallback Swap: USDC -> Token (On-Chain)
     * This replaces the LetsExchange API call if it fails.
     */
    public String swapOnChain(Credentials wallet, String tokenIn, String tokenOut, BigInteger amountIn) {
        try {
            // Function: swapExactTokensForTokens(amountIn, amountOutMin, path, to, deadline)
            Function function = new Function(
                    "swapExactTokensForTokens",
                    Arrays.asList(
                            new Uint256(amountIn),
                            new Uint256(BigInteger.ZERO), // Accept any amount (Slippage risk!)
                            new org.web3j.abi.datatypes.DynamicArray<>(Address.class, Arrays.asList(
                                    new Address(tokenIn),
                                    new Address(tokenOut)
                            )),
                            new Address(wallet.getAddress()),
                            new Uint256(System.currentTimeMillis() + 1000 * 60 * 10) // 10 min deadline
                    ),
                    Collections.emptyList()
            );

            String encodedFunction = FunctionEncoder.encode(function);

            // Create Transaction
            BigInteger nonce = arcNode.ethGetTransactionCount(wallet.getAddress(), DefaultBlockParameterName.LATEST)
                    .send().getTransactionCount();

            // Arc uses USDC as gas!
            BigInteger gasPrice = arcNode.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(200000);

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, ARC_DEX_ROUTER, BigInteger.ZERO, encodedFunction);

            // Sign & Send
            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, wallet);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction response = arcNode.ethSendRawTransaction(hexValue).send();

            if (response.hasError()) return "Error: " + response.getError().getMessage();
            return response.getTransactionHash();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}