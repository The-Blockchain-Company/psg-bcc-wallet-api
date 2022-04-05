# PSG Bcc Wallet API

_For consultancy services email [enterprise.solutions@blockchain-company.io](mailto:enterprise.solutions@blockchain-company.io)_
### Scala and Java client for the Bcc Wallet API

The Bcc node exposes a [REST like API](https://github.com/The-Blockchain-Company/bcc-wallet) 
allowing clients to perform a variety of tasks including 
 - creating or restoring a wallet
 - submitting a transaction with or without [metadata](https://github.com/The-Blockchain-Company/bcc-wallet/wiki/TxMetadata) 
 - checking on the status of the node
 - listing transactions
 - listing wallets

The full list of capabilities can be found [here](https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/). 
     
This artefact wraps calls to that API to make them easily accessible to Java or Scala developers.

It also provides an executable jar to provide rudimentary command line access. 


- [Building](#building)
- [Usage](#usage)
    - [scala](#usagescala)
    - [java](#usagejava)
- [Command line executable jar](#cmdline)
- [Examples](#examples)
- [Issues](#issues)
        

### <a name="building"></a> Building 

This is an `sbt` project, so the usual `sbt` commands apply.

Clone the [repository](https://github.com/The-Blockchain-Company/psg-bcc-wallet-api) 

To build and publish the project to your local repository use 

`sbt publish`

To build the command line executable jar use

`sbt assembly`  

To build the command line executable jar skipping tests, use

`sbt 'set test in assembly := {}' assembly`

This will create a jar in the `target/scala-2.13` folder. 

#### Implementation

The jar is part of an Akka streaming ecosystem and unsurprisingly uses [Akka Http](https://doc.akka.io/docs/akka-http/current/introduction.html) to make the http requests, 
it also uses [circe](https://circe.github.io/circe/) to marshal and unmarshal the json.

### <a name="usage"></a>Usage 

The jar is published in Maven Central, the command line executable jar can be downloaded from the releases section 
of the [github repository](https://github.com/The-Blockchain-Company/psg-bcc-wallet-api)


Before you can use this API you need a bcc wallet backend to contact, you can set one up following the instructions 
[here](https://github.com/The-Blockchain-Company/bcc-wallet). The docker setup is recommended.
 
Alternatively, for 'tire kicking' purposes you may try  `http://bcc-wallet-testnet.iog.solutions:8090/v2/`    
     
#### <a name="usagescala"></a>Scala

Add the library to your dependencies 

`libraryDependencies += "solutions.iog" %% "psg-bcc-wallet-api" % "x.x.x"`

The api calls return a HttpRequest set up to the correct url and a mapper to take the entity result and 
map it from Json to the corresponding case classes. Using `networkInfo` as an example...

```
import akka.actor.ActorSystem
import iog.psg.bcc.BccApi.BccApiOps.{BccApiRequestOps}
import iog.psg.bcc.BccApi.{BccApiResponse, ErrorMessage, defaultMaxWaitTime}
import iog.psg.bcc.{ApiRequestExecutor, BccApi}
import iog.psg.bcc.BccApiCodec.NetworkInfo

import scala.concurrent.Future

object Main {

  def main(args: Array[String]): Unit = {

    implicit val requestExecutor = ApiRequestExecutor

    implicit val as = ActorSystem("MyActorSystem")
    val baseUri = "http://localhost:8090/v2/"
    import as.dispatcher

    val api = new BccApi(baseUri)

    val networkInfoF: Future[BccApiResponse[NetworkInfo]] =
      api.networkInfo.execute // async (recommended)

    // OR use blocking version for tests 
    val networkInfo: BccApiResponse[NetworkInfo] =
      api.networkInfo.executeBlocking

    networkInfo match {
      case Left(ErrorMessage(message, code)) => //do something
      case Right(netInfo: NetworkInfo) => // good!
    }
  }
}
```
 
#### <a name="usagejava"></a>Java

First, add the library to your dependencies, 
```
<dependency>
  <groupId>solutions.iog</groupId>
  <artifactId>psg-bcc-wallet-api_2.13</artifactId>
  <version>x.x.x</version>
</dependency>
```

Then, using `getWallet` as an example...

```
import iog.psg.bcc.jpi.*;

ActorSystem as = ActorSystem.create();
ExecutorService es = Executors.newFixedThreadPool(10);
BccApiBuilder builder =
        BccApiBuilder.create("http://localhost:8090/v2/")
                .withActorSystem(as) // <- ActorSystem optional
                .withExecutorService(es); // <- ExecutorService optional

BccApi api = builder.build();

String walletId = "<PUT WALLET ID HERE>";
BccApiCodec.Wallet  wallet =
            api.getWallet(walletId).toCompletableFuture().get();

```

#### <a name="cmdline"></a>Command Line 

To see the usage instructions, use    

`java -jar psg-bcc-wallet-api-assembly-x.x.x-SNAPSHOT.jar`

For example, to see the [network information](https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Network) use 

`java -jar psg-bcc-wallet-api-assembly-x.x.x-SNAPSHOT.jar -baseUrl http://localhost:8090/v2/ -netInfo`
  
#### <a name="examples"></a> Examples

The best place to find working examples is in the [test](https://github.com/The-Blockchain-Company/psg-bcc-wallet-api/tree/develop/src/test) folder 

#### <a name="issues"></a> Issues

This release does *not* cover the entire bcc-wallet API, it focuses on getting the sophie core functionality into the hands of developers, if you need another call covered please log 
an [issue (or make a PR!)](https://github.com/The-Blockchain-Company/psg-bcc-wallet-api/issues)    
