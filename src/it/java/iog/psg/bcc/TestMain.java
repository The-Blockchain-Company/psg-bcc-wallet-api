package iog.psg.bcc;

import akka.actor.ActorSystem;
import iog.psg.bcc.jpi.BccApi;
import iog.psg.bcc.jpi.*;
import scala.Enumeration;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestMain {

    public static void main(String[] args) throws BccApiException, ExecutionException, InterruptedException {

        try {
            ActorSystem as = ActorSystem.create();
            ExecutorService es = Executors.newFixedThreadPool(10);
            BccApiBuilder builder =
                    BccApiBuilder.create("http://localhost:8090/v2/")
                            .withActorSystem(as)
                            .withExecutorService(es);

            BccApi api = builder.build();
            String passphrase = "password10";
            String menmString = "receive post siren monkey mistake morning teach section mention rural idea say offer number ribbon toward rigid pluck begin ticket auto";
            List<String> menmLst = Arrays.asList(menmString.split(" "));
            String walletId = "b63eacb4c89bd942cacfe0d3ed47459bbf0ce5c9";


            BccApiCodec.Wallet wallet = null;
            try {
                wallet =
                        api.getWallet(walletId).toCompletableFuture().get();
            } catch(Exception e) {
                wallet = api.createRestore("bccapimainspec", passphrase, menmLst, 10).toCompletableFuture().get();
            }

            BccApiCodec.WalletAddressId unusedAddr = api.listAddresses(wallet.id(), AddressFilter.UNUSED).toCompletableFuture().get().get(0);

            Enumeration.Value entropic = BccApiCodec.Units$.MODULE$.entropic();
            Map<Long, String> meta = new HashMap();
            String l = Long.toString(Long.MAX_VALUE);
            meta.put(Long.MAX_VALUE, "hello world");

            //9223372036854775807
            //meta.put(l, "0123456789012345678901234567890123456789012345678901234567890123");

            List<BccApiCodec.Payment> pays =
                    Arrays.asList(
                            new BccApiCodec.Payment(unusedAddr.id(),
                                    new BccApiCodec.QuantityUnit(1000000, entropic)
                            )
                    );
            BccApiCodec.CreateTransactionResponse resp =
                    api.createTransaction(
                            wallet.id(),
                            passphrase,
                            pays,
                            MetadataBuilder.withMap(meta),
                            "self").toCompletableFuture().get();
            System.out.println(resp.status().toString());
            System.out.println(resp.id());
            System.out.println(resp.metadata());

            //executeHelper.execute(req);
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            System.exit(9);
        }

    }
}
