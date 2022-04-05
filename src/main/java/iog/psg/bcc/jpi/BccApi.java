package iog.psg.bcc.jpi;

import iog.psg.bcc.BccApiCodec;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Defines the API which wraps the Bcc API, depends on BccApiCodec for it's implementation,
 * so clients will import the Codec also.
 */
public interface BccApi {

    /**
     * Create and restore a wallet from a mnemonic sentence or account public key.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postWallet">#postWallet</a>
     *
     * @param name wallet's name
     * @param passphrase A master passphrase to lock and protect the wallet for sensitive operation (e.g. sending funds)
     * @param mnemonicWordList A list of mnemonic words [ 15 .. 24 ] items ( can be generated using https://iancoleman.io/bip39> )
     * @param addressPoolGap An optional number of consecutive unused addresses allowed
     * @return Created wallet
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.Wallet> createRestore(
            String name,
            String passphrase,
            List<String> mnemonicWordList,
            int addressPoolGap) throws BccApiException;

    /**
     * Create and restore a wallet from a mnemonic sentence or account public key.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postWallet">#postWallet</a>
     *
     * @param name wallet's name
     * @param passphrase A master passphrase to lock and protect the wallet for sensitive operation (e.g. sending funds)
     * @param mnemonicWordList A list of mnemonic words [ 15 .. 24 ] items ( can be generated using https://iancoleman.io/bip39> )
     * @param mnemonicSecondFactor A passphrase used to encrypt the mnemonic sentence. [ 9 .. 12 ] items
     * @param addressPoolGap An optional number of consecutive unused addresses allowed
     * @return Created wallet
     * @throws BccApiException thrown on API error response, contains error message and code from API
     *
     */
    CompletionStage<BccApiCodec.Wallet> createRestore(
            String name,
            String passphrase,
            List<String> mnemonicWordList,
            List<String> mnemonicSecondFactor,
            int addressPoolGap) throws BccApiException;

    /**
     * Create and restore a wallet from a mnemonic sentence or account public key.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postWallet">#postWallet</a>
     *
     * @param name wallet's name
     * @param accountPublicKey An extended account public key (public key + chain code)
     * @param addressPoolGap An optional number of consecutive unused addresses allowed
     * @return create/restore wallet request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.Wallet> createRestoreWithKey(
            String name,
            String accountPublicKey,
            int addressPoolGap
    ) throws BccApiException;

    /**
     * Create and send transaction from the wallet.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransaction">#postTransaction</a>
     *
     * @param fromWalletId wallet's id
     * @param passphrase The wallet's master passphrase. [ 0 .. 255 ] characters
     * @param payments A list of target outputs ( address, amount )
     * @param withdrawal nullable, when provided, instruments the server to automatically withdraw rewards from the source
     *                   wallet when they are deemed sufficient (i.e. they contribute to the balance for at least as much
     *                   as they cost).
     * @param metadata   Extra application data attached to the transaction.
     * @return created transaction
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.CreateTransactionResponse> createTransaction(
            String fromWalletId,
            String passphrase,
            List<BccApiCodec.Payment> payments,
            BccApiCodec.TxMetadataIn metadata,
            String withdrawal
    ) throws BccApiException;

    /**
     * Create and send transaction from the wallet.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransaction">#postTransaction</a>
     *
     * @param fromWalletId wallet's id
     * @param passphrase The wallet's master passphrase. [ 0 .. 255 ] characters
     * @param payments A list of target outputs ( address, amount )
     * @return created transaction
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.CreateTransactionResponse> createTransaction(
            String fromWalletId,
            String passphrase,
            List<BccApiCodec.Payment> payments
    ) throws BccApiException;

    /**
     * Get wallet details by id
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getWallet">#getWallet</a>
     *
     * @param fromWalletId wallet's id
     * @return wallet
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.Wallet> getWallet(
            String fromWalletId) throws BccApiException;

    /**
     * Delete wallet by id
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/deleteWallet">#deleteWallet</a>
     *
     * @param fromWalletId wallet's id
     * @return void
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<Void> deleteWallet(
            String fromWalletId) throws BccApiException;

    /**
     * Get transaction by id.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getTransaction">#getTransaction</a>
     *
     * @param walletId wallet's id
     * @param transactionId transaction's id
     * @return get transaction request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.CreateTransactionResponse> getTransaction(
            String walletId, String transactionId) throws BccApiException;

    /**
     * Forget pending transaction
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/deleteTransaction">#deleteTransaction</a>
     *
     * @param walletId wallet's id
     * @param transactionId transaction's id
     * @return forget pending transaction request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<Void> deleteTransaction(String walletId, String transactionId) throws BccApiException;

    /**
     * Estimate fee for the transaction. The estimate is made by assembling multiple transactions and analyzing the
     * distribution of their fees. The estimated_max is the highest fee observed, and the estimated_min is the fee which
     * is lower than at least 90% of the fees observed.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransactionFee">#estimateFee</a>
     *
     * @param walletId wallet's id
     * @param payments A list of target outputs ( address, amount )
     * @return estimatedfee response
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFee(
            String walletId, List<BccApiCodec.Payment> payments) throws BccApiException;

    /**
     * Estimate fee for the transaction. The estimate is made by assembling multiple transactions and analyzing the
     * distribution of their fees. The estimated_max is the highest fee observed, and the estimated_min is the fee which
     * is lower than at least 90% of the fees observed.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransactionFee">#estimateFee</a>
     *
     * @param walletId wallet's id
     * @param payments A list of target outputs ( address, amount )
     * @param withdrawal nullable, when provided, instruments the server to automatically withdraw rewards from the source
     *                   wallet when they are deemed sufficient (i.e. they contribute to the balance for at least as much
     *                   as they cost).
     * @param metadata  Extra application data attached to the transaction.
     * @return estimated fee response
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFee(
            String walletId,
            List<BccApiCodec.Payment> payments,
            String withdrawal,
            BccApiCodec.TxMetadataIn metadata) throws BccApiException;

    /**
     * Select coins to cover the given set of payments.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Coin-Selections">#CoinSelections</a>
     *
     * @param walletId wallet's id
     * @param payments A list of target outputs ( address, amount )
     * @return fund payments
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.FundPaymentsResponse> fundPayments(
            String walletId, List<BccApiCodec.Payment> payments) throws BccApiException;

    /**
     * list of known addresses, ordered from newest to oldest
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Addresses">#Addresses</a>
     *
     *
     * @param walletId wallet's id
     * @param addressFilter addresses state: used, unused
     * @return list of wallet's addresses
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.WalletAddressId>> listAddresses(
            String walletId, AddressFilter addressFilter) throws BccApiException;

    /**
     * list of known addresses, ordered from newest to oldest
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Addresses">#Addresses</a>
     *
     * @param walletId wallet's id
     * @return list of wallet's addresses
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.WalletAddressId>> listAddresses(
            String walletId) throws BccApiException;

    /**
     * Give useful information about the structure of a given address.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/inspectAddress">#inspectAddress</a>
     *
     * @param addressId id of the address
     * @return address inspect request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.WalletAddress> inspectAddress(
            String addressId) throws BccApiException;

    /**
     * Lists all incoming and outgoing wallet's transactions.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listTransactions">#listTransactions</a>
     *
     * @param builder ListTransactionsParamBuilder
     * @return list of wallet's transactions
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.CreateTransactionResponse>> listTransactions(
            ListTransactionsParamBuilder builder) throws BccApiException;

    /**
     * list of known wallets, ordered from oldest to newest.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listWallets">#listWallets</a>
     *
     * @return wallets's list
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.Wallet>> listWallets() throws BccApiException;

    /**
     * Update Passphrase
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/putWalletPassphrase">#putWalletPassphrase</a>
     * @param walletId wallet's id
     * @param oldPassphrase current passphrase
     * @param newPassphrase new passphrase
     * @return void
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<Void> updatePassphrase(
            String walletId,
            String oldPassphrase,
            String newPassphrase) throws BccApiException;

    /**
     * Update wallet's name
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/putWallet">#putWallet</a>
     *
     * @param walletId wallet's id
     * @param name new wallet's name
     * @return update wallet request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.Wallet> updateName(
            String walletId,
            String name) throws BccApiException;

    /**
     * Gives network information
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkInformation">#getNetworkInformation</a>
     *
     * @return network info
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.NetworkInfo> networkInfo() throws BccApiException;

    /**
     * Gives network clock information
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkClock">#getNetworkClock</a>
     *
     * @return network clock info request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.NetworkClock> networkClock() throws BccApiException;

    /**
     * Gives network clock information
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkClock">#getNetworkClock</a>
     *
     * @param forceNtpCheck When this flag is set, the request will block until NTP server responds or will timeout after a while without any answer from the NTP server.
     * @return network clock info request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.NetworkClock> networkClock(Boolean forceNtpCheck) throws BccApiException;

    /**
     * Gives network parameters
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkParameters">#getNetworkParameters</a>
     *
     * @return network parameters request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.NetworkParameters> networkParameters() throws BccApiException;

    /**
     * Return the UTxOs distribution across the whole wallet, in the form of a histogram
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getUTxOsStatistics">#getUTxOsStatistics</a>
     *
     * @param walletId wallet's id
     * @return get UTxOs statistics request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.UTxOStatistics> getUTxOsStatistics(String walletId) throws BccApiException;

    /**
     * Submits a transaction that was created and signed outside of bcc-wallet.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postExternalTransaction">#postExternalTransaction</a>
     *
     * @param binary message binary blob string
     * @return post external transaction request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.PostExternalTransactionResponse> postExternalTransaction(String binary) throws BccApiException;

    /**
     * Submit one or more transactions which transfers all funds from a Sophie wallet to a set of addresses.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/migrateSophieWallet">#migrateSophieWallet</a>
     *
     * @param walletId wallet's id
     * @param passphrase wallet's master passphrase
     * @param addresses recipient addresses
     * @return migrate sophie wallet request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.MigrationResponse>> migrateSophieWallet(String walletId, String passphrase, List<String> addresses) throws BccApiException;

    /**
     * Calculate the exact cost of sending all funds from particular Sophie wallet to a set of addresses
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getSophieWalletMigrationInfo">#getSophieWalletMigrationInfo</a>
     * @param walletId wallet's id
     * @return migration cost request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.MigrationCostResponse> getSophieWalletMigrationInfo(String walletId) throws BccApiException;

    /**
     * List all known stake pools ordered by descending non_myopic_member_rewards.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listStakePools">#listStakePools</a>
     *
     * @param stake The stake the user intends to delegate in Entropic. Required.
     * @return list stake pools request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<List<BccApiCodec.StakePool>> listStakePools(Integer stake) throws BccApiException;

    /**
     * Estimate fee for joining or leaving a stake pool
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getDelegationFee">#getDelegationFee</a>
     *
     * @param walletId wallet's id
     * @return estimate fee request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFeeStakePool(String walletId) throws BccApiException;

    /**
     * Delegate all (current and future) addresses from the given wallet to the given stake pool.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/joinStakePool">#joinStakePool</a>
     *
     * @param walletId wallet's id
     * @param stakePoolId stakePool's id
     * @param passphrase wallet's passphrase
     * @return quit stake pool request
     */
    CompletionStage<BccApiCodec.MigrationResponse> joinStakePool(String walletId, String stakePoolId, String passphrase) throws BccApiException;

    /**
     * Stop delegating completely. The wallet's stake will become inactive.
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/quitStakePool">#quitStakePool</a>
     *
     * @param walletId wallet's id
     * @param passphrase wallet's passphrase
     * @return quit stake pool request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.MigrationResponse> quitStakePool(String walletId, String passphrase) throws BccApiException;

    /**
     * View maintenance actions
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getMaintenanceActions">#getMaintenanceActions</a>
     *
     * @return the current status of the stake pools maintenance actions request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<BccApiCodec.StakePoolMaintenanceActionsStatus> getMaintenanceActions() throws BccApiException;

    /**
     * Trigger Maintenance actions
     * Api Url: <a href="https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postMaintenanceAction">#postMaintenanceAction</a>
     *
     * @return Trigger Maintenance actions request
     * @throws BccApiException thrown on API error response, contains error message and code from API
     */
    CompletionStage<Void> postMaintenanceAction() throws BccApiException;
}
