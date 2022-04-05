package iog.psg.bcc.jpi;

import akka.actor.ActorSystem;
import scala.concurrent.ExecutionContext;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BccApiBuilder {

    private final String url;
    private ExecutorService executorService;
    private ActorSystem actorSystem;
    private ApiRequestExecutor apiRequestExecutor;

    private BccApiBuilder() {
        url = null;
    }

    private BccApiBuilder(String url) {
        this.url = url;
        Objects.requireNonNull(url,
                "Provide the url to a bcc wallet instance e.g. http://127.0.0.1:8090/v2/");
    }

    public static BccApiBuilder create(String url) {
        return new BccApiBuilder(url);
    }

    public BccApiBuilder withExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
        Objects.requireNonNull(executorService, "ExecutorService is 'null'");
        return this;
    }


    public BccApiBuilder withActorSystem(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        Objects.requireNonNull(actorSystem, "ActorSystem is 'null'");
        return this;
    }

    public BccApiBuilder withApiExecutor(ApiRequestExecutor apiExecutor) {
        this.apiRequestExecutor = apiExecutor;
        Objects.requireNonNull(apiExecutor, "apiExecutor is 'null'");
        return this;
    }

    public BccApi build() {

        if (actorSystem == null) {
            actorSystem = ActorSystem.create("BccJPIActorSystem");
        }

        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }

        ExecutionContext ec = ExecutionContext.fromExecutorService(executorService);

        HelpExecute helpExecute;

        if (apiRequestExecutor == null) {
            helpExecute = new HelpExecute(ec, actorSystem);
        } else {
            helpExecute = new HelpExecute(ec, actorSystem) {
                @Override
                public <T> CompletionStage<T> execute(iog.psg.bcc.BccApi.BccApiRequest<T> request) throws BccApiException {
                    return apiRequestExecutor.execute(request);
                }
            };
        }

        iog.psg.bcc.BccApi api = iog.psg.bcc.BccApi.apply(url, ec, actorSystem);

        return new BccApiImpl(api, helpExecute);
    }

}
