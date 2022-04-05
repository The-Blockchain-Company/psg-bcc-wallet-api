package iog.psg.bcc.jpi;

import akka.actor.ActorSystem;
import iog.psg.bcc.BccApiCodec;
import scala.Enumeration;
import scala.Option;
import scala.concurrent.Future;
import scala.jdk.CollectionConverters;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import static scala.compat.java8.FutureConverters.*;
import scala.util.Either;

public class JpiResponseCheck {

    public final BccApi jpi;
    private final long timeout;
    private final TimeUnit timeoutUnit;

    private JpiResponseCheck() {
        jpi = null;
        timeout = 0;
        timeoutUnit = null;
    }

    public JpiResponseCheck(BccApi jpi, long timeout, TimeUnit timeoutUnit) {
        this.jpi = jpi;
        this.timeoutUnit = timeoutUnit;
        this.timeout = timeout;
    }

    static String get(BccApiCodec.NetworkInfo info) {
        return info.syncProgress().status().toString();
    }

    public void createBadWallet() throws BccApiException, InterruptedException, TimeoutException, ExecutionException {
        List<String> mnem = Arrays.asList("", "sdfa", "dfd");
        jpi.createRestore("some name", "password99", mnem,4).toCompletableFuture().get(timeout, timeoutUnit);
    }

    public boolean findOrCreateTestWallet(String ourWalletId, String ourWalletName, String walletPassphrase, List<String> wordList, int addressPoolGap) throws BccApiException, InterruptedException, TimeoutException, ExecutionException {
        List<BccApiCodec.Wallet> wallets = jpi.listWallets().toCompletableFuture().get(timeout, timeoutUnit);
        for(BccApiCodec.Wallet w: wallets) {
            if(w.id().contentEquals(ourWalletId)) {
                return true;
            }
        }

        BccApiCodec.Wallet created = createTestWallet(ourWalletName, walletPassphrase, wordList, addressPoolGap);
        return created.id().contentEquals(ourWalletId);
    }

    public BccApiCodec.Wallet createTestWallet(String ourWalletName, String walletPassphrase, List<String> wordList, int addressPoolGap) throws BccApiException, InterruptedException, ExecutionException, TimeoutException {
        BccApiCodec.Wallet wallet = jpi.createRestore(ourWalletName, walletPassphrase, wordList, addressPoolGap).toCompletableFuture().get(timeout, timeoutUnit);
        return wallet;
    }

    public BccApiCodec.Wallet createTestWallet(String ourWalletName, String walletPassphrase, List<String> wordList, List<String> mnemSecondaryWordList, int addressPoolGap) throws BccApiException, InterruptedException, ExecutionException, TimeoutException {
        BccApiCodec.Wallet wallet = jpi.createRestore(ourWalletName, walletPassphrase, wordList, mnemSecondaryWordList, addressPoolGap).toCompletableFuture().get(timeout, timeoutUnit);
        return wallet;
    }

    public boolean getWallet(String walletId) throws BccApiException, InterruptedException, TimeoutException, ExecutionException {
        BccApiCodec.Wallet w = jpi.getWallet(walletId).toCompletableFuture().get(timeout, timeoutUnit);
        return w.id().contentEquals(walletId);
    }

    public String updateWalletName(String walletId, String name) throws BccApiException, ExecutionException, InterruptedException, TimeoutException {
        CompletionStage<BccApiCodec.Wallet> walletCS = jpi.updateName(walletId, name);
        return walletCS.thenApply(w -> w.name()).toCompletableFuture().get(timeout, timeoutUnit);
    }

    public void passwordChange(String walletId, String passphrase, String newPassphrase) throws BccApiException, InterruptedException, ExecutionException, TimeoutException {
        jpi.updatePassphrase(walletId, passphrase, newPassphrase).toCompletableFuture().get(timeout, timeoutUnit);
    }


    public BccApiCodec.FundPaymentsResponse fundPayments(String walletId, long amountToTransfer) throws Exception {
        List<BccApiCodec.WalletAddressId> unused = jpi.listAddresses(walletId, AddressFilter.UNUSED).toCompletableFuture().get(timeout, timeoutUnit);
        String unusedAddrId = unused.get(0).id();
        BccApiCodec.QuantityUnit amount = new BccApiCodec.QuantityUnit(amountToTransfer, BccApiCodec.Units$.MODULE$.entropic());
        BccApiCodec.Payment p = new BccApiCodec.Payment(unusedAddrId, amount);
        BccApiCodec.FundPaymentsResponse response = jpi.fundPayments(walletId, Collections.singletonList(p)).toCompletableFuture().get(timeout, timeoutUnit);
        return response;
    }

    public void deleteWallet(String walletId) throws Exception {
        jpi.deleteWallet(walletId).toCompletableFuture().get(timeout, timeoutUnit);

    }

    public BccApiCodec.CreateTransactionResponse paymentToSelf(String wallet1Id, String passphrase, long amountToTransfer, Map<String, String> metadata) throws Exception {

        Map<Long, String> metadataLongKey = new HashMap();
        metadata.forEach((k,v) -> {
            metadataLongKey.put(Long.parseLong(k), v);
        });

        BccApiCodec.TxMetadataMapIn in = MetadataBuilder.withMap(metadataLongKey);
        List<BccApiCodec.WalletAddressId> unused = jpi.listAddresses(wallet1Id, AddressFilter.UNUSED).toCompletableFuture().get(timeout, timeoutUnit);
        String unusedAddrIdWallet1 = unused.get(0).id();
        BccApiCodec.QuantityUnit amount = new BccApiCodec.QuantityUnit(amountToTransfer, BccApiCodec.Units$.MODULE$.entropic());
        List<BccApiCodec.Payment> payments = Collections.singletonList(new BccApiCodec.Payment(unusedAddrIdWallet1, amount));
        jpi.estimateFee(wallet1Id, payments).toCompletableFuture().get(timeout, timeoutUnit);
        return jpi.createTransaction(wallet1Id, passphrase, payments, in, null).toCompletableFuture().get(timeout, timeoutUnit);

    }

    public BccApiCodec.CreateTransactionResponse getTx(String walletId, String txId) throws Exception {
        return jpi.getTransaction(walletId, txId).toCompletableFuture().get(timeout, timeoutUnit);
    }

    public static BccApi buildWithPredefinedApiExecutor(iog.psg.bcc.ApiRequestExecutor executor, ActorSystem as) {
        BccApiBuilder builder = BccApiBuilder.create("http://fake:1234/").withApiExecutor(new ApiRequestExecutor() {
            @Override
            public <T> CompletionStage<T> execute(iog.psg.bcc.BccApi.BccApiRequest<T> request) throws BccApiException {
                Future<Either<iog.psg.bcc.BccApi.ErrorMessage, T>> sResponse = executor.execute(request, as.dispatcher(), as);
                CompletionStage<T> jResponse = toJava(HelpExecute.unwrap(sResponse, as.dispatcher()));
                return jResponse;
            }
        });

        return builder.build();
    }

    public static BccApi buildWithDummyApiExecutor() {
        BccApiBuilder builder = BccApiBuilder.create("http://fake/").withApiExecutor(new ApiRequestExecutor() {
            @Override
            public <T> CompletionStage<T> execute(iog.psg.bcc.BccApi.BccApiRequest<T> request) throws BccApiException {
                CompletableFuture<T> result = new CompletableFuture<>();

                if(request.request().uri().path().endsWith("wallets", true)) {
                    Enumeration.Value entropic = BccApiCodec.Units$.MODULE$.Value(BccApiCodec.Units$.MODULE$.entropic().toString());
                    Enumeration.Value sync = BccApiCodec.SyncState$.MODULE$.Value(BccApiCodec.SyncState$.MODULE$.ready().toString());
                    BccApiCodec.QuantityUnit dummy = new BccApiCodec.QuantityUnit(1, entropic);
                    BccApiCodec.SyncStatus state = new BccApiCodec.SyncStatus(
                            sync,
                            Option.apply(null)
                    );
                    BccApiCodec.NetworkTip tip = new BccApiCodec.NetworkTip(3,4,Option.apply(null), Option.apply(10));

                    ZonedDateTime dummyDate = ZonedDateTime.parse("2000-01-02T10:01:02+01:00");
                    Enumeration.Value delegatingStatus = BccApiCodec.DelegationStatus$.MODULE$.Value(BccApiCodec.DelegationStatus$.MODULE$.delegating().toString());
                    Enumeration.Value notDelegatingStatus = BccApiCodec.DelegationStatus$.MODULE$.Value(BccApiCodec.DelegationStatus$.MODULE$.notDelegating().toString());
                    BccApiCodec.DelegationActive delegationActive = new BccApiCodec.DelegationActive(delegatingStatus, Option.apply("1234567890"));
                    BccApiCodec.DelegationNext delegationNext = new BccApiCodec.DelegationNext(notDelegatingStatus, Option.apply(new BccApiCodec.NextEpoch(dummyDate, 10)));
                    List<BccApiCodec.DelegationNext> nexts =  Arrays.asList(delegationNext);
                    scala.collection.immutable.List<BccApiCodec.DelegationNext> nextsScalaList = CollectionConverters.ListHasAsScala(nexts).asScala().toList();
                    BccApiCodec.Delegation delegation = new BccApiCodec.Delegation(delegationActive, nextsScalaList);

                    result.complete((T) new BccApiCodec.Wallet(
                            "id",
                            10,
                            new BccApiCodec.Balance(dummy, dummy, dummy),
                            Option.apply(delegation),
                            "name",
                            Option.apply(new BccApiCodec.Passphrase(dummyDate)),
                            state,
                            tip));
                    return result.toCompletableFuture();
                } else {
                    throw new BccApiException("Unexpected", "request");
                }
            }

        });

        return builder.build();
    }
}
