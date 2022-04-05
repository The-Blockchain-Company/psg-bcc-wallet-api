package iog.psg.bcc.jpi;

import java.util.concurrent.CompletionStage;

public interface ApiRequestExecutor {
    <T> CompletionStage<T> execute(iog.psg.bcc.BccApi.BccApiRequest<T> request) throws BccApiException;

}
