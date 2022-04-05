package iog.psg.bcc

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import iog.psg.bcc.BccApi.{BccApiRequest, BccApiResponse}

import scala.concurrent.{ExecutionContext, Future}

object ApiRequestExecutor extends ApiRequestExecutor

trait ApiRequestExecutor {

  def execute[T](request: BccApiRequest[T])(implicit ec: ExecutionContext, as: ActorSystem): Future[BccApiResponse[T]] =
    Http()
      .singleRequest(request.request)
      .flatMap(request.mapper)

}
