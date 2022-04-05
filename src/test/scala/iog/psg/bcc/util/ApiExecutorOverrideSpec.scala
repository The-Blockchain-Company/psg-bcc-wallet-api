package iog.psg.bcc.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import iog.psg.bcc.BccApi.{BccApiRequest, BccApiResponse, ErrorMessage}
import iog.psg.bcc.{ApiRequestExecutor, BccApi}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

class ApiExecutorOverrideSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  private implicit val system = ActorSystem("SingleRequest")
  import system.dispatcher

  "A client in different package" should "be able to override the APIExecutor" in {
    //This will not compile if the trait is sealed.
    val testUri = "http://localhost:9999/"
    val response = Left(ErrorMessage("TESTAPIOVERRIDE", "TESTAPIOVERRIDE"))
    val sut = new ApiRequestExecutor {
      override def execute[T](request: BccApi.BccApiRequest[T])(implicit ec: ExecutionContext, as: ActorSystem): Future[BccApiResponse[T]] = {
        request.request.uri.toString() shouldBe testUri
        Future.successful(response)
      }
    }


    val result = sut.execute(BccApiRequest(
      HttpRequest(uri = testUri),
      _ => Future.successful(response)
    )).futureValue

    result shouldBe response
  }
}
