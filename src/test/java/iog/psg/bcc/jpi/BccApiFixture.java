package iog.psg.bcc.jpi;

import akka.actor.ActorSystem;

import java.util.Objects;

public class BccApiFixture {

    public BccApi getJpi() {
        return jpi;
    }

    private final BccApi jpi;

    private BccApiFixture() {
        jpi = null;
    }

    public BccApiFixture(String url) {
        Objects.requireNonNull(url);
        ActorSystem as = ActorSystem.create("TESTING_BCC_JPI");
        jpi = BccApiBuilder.create(url).withActorSystem(as).build();
    }
}
