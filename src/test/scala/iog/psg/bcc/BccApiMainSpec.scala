package iog.psg.bcc

import akka.actor.ActorSystem
import iog.psg.bcc.BccApi.{BccApiResponse, ErrorMessage}
import iog.psg.bcc.BccApiCodec.NetworkInfo
import iog.psg.bcc.BccApiMain.CmdLine
import iog.psg.bcc.util.{ArgumentParser, DummyModel, ModelCompare, Trace}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}


class BccApiMainSpec extends AnyFlatSpec with Matchers with ModelCompare with DummyModel {

  "The Cmd Line -netInfo" should "show current network information" in new ApiRequestExecutorFixture[NetworkInfo]{
    override val expectedRequestUrl: String = "http://127.0.0.1:8090/v2/network/information"
    override val response: BccApiResponse[NetworkInfo] = Right(networkInfo)
    override val args: Array[String] = Array(CmdLine.netInfo)

    getTraceResults should include(""""status" : "ready"""")
  }

  it should "fail with exception during executing request" in new ApiRequestExecutorFixture[NetworkInfo] {
    override val expectedRequestUrl: String = "http://127.0.0.1:8090/v2/network/information"
    override val response: BccApiResponse[NetworkInfo] = Right(networkInfo)
    override val args: Array[String] = Array(CmdLine.netInfo)

    override implicit val apiExecutor = new ApiRequestExecutor {
      override def execute[T](request: BccApi.BccApiRequest[T])(implicit ec: ExecutionContext, as: ActorSystem): Future[BccApiResponse[T]] = {
        Future.failed(new RuntimeException("Test failed."))
      }.asInstanceOf[Future[BccApiResponse[T]]]
    }

    getTraceResults shouldBe "baseurl:http://127.0.0.1:8090/v2/, -netInfo, java.lang.RuntimeException: Test failed."
  }

  it should "return an API error" in new ApiRequestExecutorFixture[NetworkInfo] {
    override val expectedRequestUrl: String = "http://127.0.0.1:8090/v2/network/information"
    override val response: BccApiResponse[NetworkInfo] = Left(ErrorMessage("Test error.", "12345"))
    override val args: Array[String] = Array(CmdLine.netInfo)

    getTraceResults shouldBe "baseurl:http://127.0.0.1:8090/v2/, -netInfo, API Error message Test error., code 12345"
  }

  private sealed trait ApiRequestExecutorFixture[T] {
    val expectedRequestUrl: String
    val response: BccApiResponse[T]
    val args: Array[String]

    lazy val arguments = new ArgumentParser(args)

    private val traceResults: ArrayBuffer[String] = ArrayBuffer.empty

    implicit private val memTrace = new Trace {
      override def apply(s: String): Unit = traceResults += s
      override def close(): Unit = ()
    }

    implicit val apiExecutor = new ApiRequestExecutor {
      override def execute[T](request: BccApi.BccApiRequest[T])(implicit ec: ExecutionContext, as: ActorSystem): Future[BccApiResponse[T]] = {
        request.request.uri.toString() shouldBe expectedRequestUrl
        Future.successful(response)
      }.asInstanceOf[Future[BccApiResponse[T]]]
    }

    final def getTraceResults: String = {
      traceResults.clear()
      BccApiMain.run(arguments)
      traceResults.mkString(", ")
    }
  }
}
