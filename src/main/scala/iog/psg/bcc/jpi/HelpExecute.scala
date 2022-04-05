package iog.psg.bcc.jpi

import java.util.concurrent.CompletionStage

import iog.psg.bcc.ApiRequestExecutor
import iog.psg.bcc.jpi.{ApiRequestExecutor => JApiRequestExecutor}
import akka.actor.ActorSystem
import iog.psg.bcc.BccApi.BccApiOps.BccApiRequestOps
import iog.psg.bcc.BccApi.{BccApiResponse, ErrorMessage}
import iog.psg.bcc.BccApiCodec.{MetadataValue, MetadataValueStr}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.javaapi.FutureConverters


class BccApiException(message: String, code: String) extends Exception(s"Message: $message, Code: $code")

object HelpExecute {

  def toScalaImmutable[B](in: java.util.Map[java.lang.Long, String]): Map[java.lang.Long, String] = in.asScala.toMap

  def toMetadataMap(in: java.util.Map[java.lang.Long, String]): Map[Long, MetadataValue] = {
    in.asScala.map {
      case (k, v) => k.toLong -> MetadataValueStr (v)
    }
  }.toMap

  def unwrap[T](responseF: Future[BccApiResponse[T]])(implicit ec: ExecutionContext): Future[T] = for {
    either <- responseF
    response <- either match {
      case Left(error) => Future.failed(new BccApiException(error.message, error.code))
      case Right(value) => Future.successful(value)
    }
  } yield response

}

class HelpExecute(implicit ec: ExecutionContext, as: ActorSystem) extends JApiRequestExecutor {

  implicit val executor: ApiRequestExecutor = ApiRequestExecutor

  @throws(classOf[BccApiException])
  private def unwrapResponse[T](resp: BccApiResponse[T]): T = resp match {
    case Right(t) => t
    case Left(ErrorMessage(message, code)) =>
      throw new BccApiException(message, code)
  }

  @throws(classOf[BccApiException])
  def execute[T](request: iog.psg.bcc.BccApi.BccApiRequest[T]): CompletionStage[T] = {
    FutureConverters.asJava(request.execute.map(unwrapResponse))
  }

  @throws(classOf[BccApiException])
  def execute[T](request: Future[iog.psg.bcc.BccApi.BccApiRequest[T]]): CompletionStage[T] = {
    FutureConverters.asJava(request).thenCompose(request => this.execute(request))
  }

  def toScalaImmutable[B](in: java.util.Map[java.lang.Long, String]): Map[java.lang.Long, String] =
    HelpExecute.toScalaImmutable(in)

}
