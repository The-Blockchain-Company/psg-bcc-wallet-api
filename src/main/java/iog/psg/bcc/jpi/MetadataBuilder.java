package iog.psg.bcc.jpi;

import io.circe.Json;
import iog.psg.bcc.BccApiCodec;

import java.util.Map;

public class MetadataBuilder {

    private MetadataBuilder() { }

    public static BccApiCodec.JsonMetadata withJson(Json metadataCompliantJson) {
        return new BccApiCodec.JsonMetadata(metadataCompliantJson);
    }

    public static BccApiCodec.JsonMetadata withJsonString(String metadataCompliantJson) {
        return BccApiCodec.JsonMetadata$.MODULE$.apply(metadataCompliantJson);
    }

    public static BccApiCodec.TxMetadataMapIn withMap(Map<Long, String> metadataMap) {
        return new BccApiCodec.TxMetadataMapIn(
                HelpExecute$.MODULE$.toMetadataMap(metadataMap));
    }

}
