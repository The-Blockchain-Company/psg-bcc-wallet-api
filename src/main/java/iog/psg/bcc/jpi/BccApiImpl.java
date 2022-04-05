package iog.psg.bcc.jpi;

import iog.psg.bcc.BccApiCodec;
import scala.Enumeration;
import scala.Some;
import scala.collection.immutable.IndexedSeq;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;


public class BccApiImpl implements BccApi {

    private final iog.psg.bcc.BccApi api;
    private final HelpExecute helpExecute;

    private BccApiImpl() {
        helpExecute = null;
        api = null;
    }

    /**
     * BccApi constructor
     *
     * @param api iog.psg.bcc.BccApi instance
     * @param helpExecute og.psg.bcc.jpi.HelpExecute instance
     */
    public BccApiImpl(iog.psg.bcc.BccApi api, HelpExecute helpExecute) {
        this.helpExecute = helpExecute;
        this.api = api;
        Objects.requireNonNull(api, "Api cannot be null");
        Objects.requireNonNull(helpExecute, "HelpExecute cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.Wallet> createRestore(
            String name,
            String passphrase,
            List<String> mnemonicWordList,
            int addressPoolGap) throws BccApiException {
        return createRestore(name, passphrase, mnemonicWordList, null, addressPoolGap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.Wallet> createRestore(
            String name,
            String passphrase,
            List<String> mnemonicWordList,
            List<String> mnemonicSecondFactor,
            int addressPoolGap) throws BccApiException {
        BccApiCodec.MnemonicSentence mnem = createMnemonic(mnemonicWordList);

        Optional<BccApiCodec.MnemonicSentence> mnemonicSecondaryFactorOpt = Optional.empty();
        if (mnemonicSecondFactor != null) {
            BccApiCodec.MnemonicSentence mnemonicSentence = createMnemonicSecondary(mnemonicSecondFactor);
            mnemonicSecondaryFactorOpt = Optional.of(mnemonicSentence);
        }

        return helpExecute.execute(
                api.createRestoreWallet(name, passphrase, mnem, option(mnemonicSecondaryFactorOpt), option(addressPoolGap))
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.Wallet> createRestoreWithKey(
            String name,
            String accountPublicKey,
            int addressPoolGap
    ) throws BccApiException {
        return helpExecute.execute(
                api.createRestoreWalletWithKey(name, accountPublicKey, option(addressPoolGap))
        );
    }

    /**
     * {@inheritDoc}
     */
   @Override
   public CompletionStage<BccApiCodec.CreateTransactionResponse> createTransaction(
           String fromWalletId,
           String passphrase,
           List<BccApiCodec.Payment> payments,
           BccApiCodec.TxMetadataIn metadata,
           String withdrawal
   ) throws BccApiException {

        return helpExecute.execute(api.createTransaction(fromWalletId, passphrase,
                new BccApiCodec.Payments(CollectionConverters.asScala(payments).toSeq()),
                option(metadata),
                option(withdrawal)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.CreateTransactionResponse> createTransaction(
            String fromWalletId,
            String passphrase,
            List<BccApiCodec.Payment> payments
    ) throws BccApiException {

        return createTransaction(fromWalletId, passphrase, payments, null, "self");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.Wallet> getWallet(
            String fromWalletId) throws BccApiException {

        return helpExecute.execute(
                api.getWallet(fromWalletId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Void> deleteWallet(
            String fromWalletId) throws BccApiException {

        return helpExecute.execute(
                api.deleteWallet(fromWalletId)).thenApply(x -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.CreateTransactionResponse> getTransaction(
            String walletId, String transactionId) throws BccApiException {

        return helpExecute.execute(
                api.getTransaction(walletId, transactionId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Void> deleteTransaction(String walletId, String transactionId) throws BccApiException {
        return helpExecute.execute(api.deleteTransaction(walletId, transactionId)).thenApply(x -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFee(
            String walletId, List<BccApiCodec.Payment> payments) throws BccApiException {
        return estimateFee(walletId, payments, "self", null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFee(
            String walletId,
            List<BccApiCodec.Payment> payments,
            String withdrawal,
            BccApiCodec.TxMetadataIn metadata) throws BccApiException {

        return helpExecute.execute(
                api.estimateFee(walletId,
                        new BccApiCodec.Payments(CollectionConverters.asScala(payments).toSeq()),
                        option(withdrawal), option(metadata)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.FundPaymentsResponse> fundPayments(
            String walletId, List<BccApiCodec.Payment> payments) throws BccApiException {
        return helpExecute.execute(
                api.fundPayments(walletId,
                        new BccApiCodec.Payments(CollectionConverters.asScala(payments).toSeq())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.WalletAddressId>> listAddresses(
            String walletId, AddressFilter addressFilter) throws BccApiException {

        Optional<Enumeration.Value> addressFilterOpt = Optional.empty();
        if (addressFilter != null) {
            Enumeration.Value v = BccApiCodec.AddressFilter$.MODULE$.Value(addressFilter.name().toLowerCase());
            addressFilterOpt = Optional.of(v);
        }

        return helpExecute.execute(
                api.listAddresses(walletId, option(addressFilterOpt))).thenApply(CollectionConverters::asJava);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.WalletAddressId>> listAddresses(
            String walletId) throws BccApiException {
        return listAddresses(walletId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.WalletAddress> inspectAddress(
            String addressId) throws BccApiException {
        return helpExecute.execute(api.inspectAddress(addressId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.CreateTransactionResponse>> listTransactions(
            ListTransactionsParamBuilder builder) throws BccApiException {
        return helpExecute.execute(
                api.listTransactions(
                        builder.getWalletId(),
                        option(builder.getStartTime()),
                        option(builder.getEndTime()),
                        builder.getOrder(),
                        option(builder.getMinwithdrawal())))
                .thenApply(CollectionConverters::asJava);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.Wallet>> listWallets() throws BccApiException {
        return helpExecute.execute(
                api.listWallets())
                .thenApply(CollectionConverters::asJava);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Void> updatePassphrase(
            String walletId,
            String oldPassphrase,
            String newPassphrase) throws BccApiException {

        return helpExecute.execute(api.updatePassphrase(walletId, oldPassphrase, newPassphrase)).thenApply(x -> null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.Wallet> updateName(
            String walletId,
            String name) throws BccApiException {
        return helpExecute.execute(api.updateName(walletId, name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.NetworkInfo> networkInfo() throws BccApiException {
        return helpExecute.execute(api.networkInfo());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.NetworkClock> networkClock() throws BccApiException {
        return networkClock(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.NetworkClock> networkClock(Boolean forceNtpCheck) throws BccApiException {
        return helpExecute.execute(api.networkClock(option(forceNtpCheck)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.NetworkParameters> networkParameters() throws BccApiException {
        return helpExecute.execute(api.networkParameters());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.UTxOStatistics> getUTxOsStatistics(String walletId) throws BccApiException {
        return helpExecute.execute(api.getUTxOsStatistics(walletId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.PostExternalTransactionResponse> postExternalTransaction(String binary) throws BccApiException {
        return helpExecute.execute(api.postExternalTransaction(binary));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.MigrationResponse>> migrateSophieWallet(String walletId, String passphrase, List<String> addresses) throws BccApiException {
        IndexedSeq<String> addressesList = CollectionConverters.asScala(addresses).toIndexedSeq();
        CompletionStage<Seq<BccApiCodec.MigrationResponse>> response = helpExecute.execute(api.migrateSophieWallet(walletId, passphrase, addressesList));
        return response.thenApply(CollectionConverters::asJava);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.MigrationCostResponse> getSophieWalletMigrationInfo(String walletId) throws BccApiException {
        return helpExecute.execute(api.getSophieWalletMigrationInfo(walletId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<List<BccApiCodec.StakePool>> listStakePools(Integer stake) throws BccApiException {
        CompletionStage<Seq<BccApiCodec.StakePool>> stakePools = helpExecute.execute(api.listStakePools(stake));
        return stakePools.thenApply(CollectionConverters::asJava);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.EstimateFeeResponse> estimateFeeStakePool(String walletId) throws BccApiException {
        return helpExecute.execute(api.estimateFeeStakePool(walletId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.MigrationResponse> joinStakePool(String walletId, String stakePoolId, String passphrase) throws BccApiException {
        return helpExecute.execute(api.joinStakePool(walletId, stakePoolId, passphrase));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.MigrationResponse> quitStakePool(String walletId, String passphrase) throws BccApiException {
        return helpExecute.execute(api.quitStakePool(walletId, passphrase));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<BccApiCodec.StakePoolMaintenanceActionsStatus> getMaintenanceActions() throws BccApiException {
        return helpExecute.execute(api.getMaintenanceActions());
    }

    @Override
    public CompletionStage<Void> postMaintenanceAction() throws BccApiException {
        return helpExecute.execute(api.postMaintenanceAction()).thenApply(x -> null);
    }

    private static <T> scala.Option<T> option(final T value) {
        return (value != null) ? new Some<T>(value) : scala.Option.apply((T) null);
    }

    private static <T> scala.Option<T> option(final Optional<T> value) {
        return value.map(BccApiImpl::option).orElse(scala.Option.apply((T) null));
    }

    private static BccApiCodec.GenericMnemonicSentence createMnemonic(List<String> wordList) {
        return new BccApiCodec.GenericMnemonicSentence(
                CollectionConverters.asScala(wordList).toIndexedSeq()
        );
    }

    private static BccApiCodec.GenericMnemonicSecondaryFactor createMnemonicSecondary(List<String> wordList) {
        return new BccApiCodec.GenericMnemonicSecondaryFactor(
                CollectionConverters.asScala(wordList).toIndexedSeq()
        );
    }

}
