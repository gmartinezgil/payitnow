package ai.payitnow.llm.infra;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;

public class CryptoService {

    // RPC Endpoints (Replace with your Infura/Alchemy/QuickNode keys)
    private final Web3j ethNode = Web3j.build(new HttpService("https://mainnet.infura.io/v3/c7b69a99e891405aaceac7ae7e0bc56d"));
    private final Web3j bscNode = Web3j.build(new HttpService("https://bsc-dataseed.binance.org/"));
    private final Web3j arcNode = Web3j.build(new HttpService("https://testnet.arc.network")); // Arc Testnet

    /**
     * Checks balance on the requested chain.
     */
    public String checkBalance(String address, String currency) throws Exception {
        Web3j targetNode;

        // Select Network based on currency
        if (currency.equalsIgnoreCase("ETH")) targetNode = ethNode;
        else if (currency.equalsIgnoreCase("BNB")) targetNode = bscNode;
        else if (currency.equalsIgnoreCase("USDC")) targetNode = arcNode; // Arc uses native USDC
        else targetNode = ethNode; // Default

        // Get Native Balance (Gas Token)
        BigInteger wei = targetNode.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send().getBalance();

        BigDecimal amount = Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        return amount.toPlainString();
    }

    // NOTE: For ERC-20 tokens (like USDT on Ethereum), you would add a method here
    // using 'ethCall' to query the Token Contract's 'balanceOf' function.
}