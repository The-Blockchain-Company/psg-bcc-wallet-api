package iog.psg.bcc

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{ Marshal, Marshaller }
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.generic.extras.Configuration
import iog.psg.bcc.BccApi.Order.Order

import scala.concurrent.{ ExecutionContext, Future }

private class BccApiImpl(baseUriWithPort: String)(implicit ec: ExecutionContext, as: ActorSystem)
    extends BccApi {

  import BccApiCodec._
  import BccApiCodec.ImplicitCodecs._
  import AddressFilter.AddressFilter
  import iog.psg.bcc.BccApi._

  private val addresses = s"${baseUriWithPort}addresses"
  private val proxy = s"${baseUriWithPort}proxy"
  private val wallets = s"${baseUriWithPort}wallets"
  private val network = s"${baseUriWithPort}network"
  private val stakePools = s"${baseUriWithPort}stake-pools"
  private def generateMigrationsUrl(walletId: String) = s"$wallets/$walletId/migrations"

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  /**
   * @inheritdoc
   */
  override def listWallets: BccApiRequest[Seq[Wallet]] = BccApiRequest(
    HttpRequest(
      uri = wallets,
      method = GET
    ),
    _.toWallets
  )

  /**
   * @inheritdoc
   */
  override def getWallet(walletId: String): BccApiRequest[Wallet] = BccApiRequest(
    HttpRequest(
      uri = s"$wallets/$walletId",
      method = GET
    ),
    _.toWallet
  )

  /**
   * @inheritdoc
   */
  override def updateName(walletId: String, name: String): Future[BccApiRequest[Wallet]] = {
    val body = Map("name" -> name)
    Marshal(body).to[RequestEntity].map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$wallets/$walletId",
          entity = marshalled,
          method = PUT
        ),
        _.toWallet
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def networkInfo: BccApiRequest[NetworkInfo] = BccApiRequest(
    HttpRequest(
      uri = s"${network}/information",
      method = GET
    ),
    _.toNetworkInfoResponse
  )

  /**
   * @inheritdoc
   */
  override def networkClock(forceNtpCheck: Option[Boolean] = None): BccApiRequest[NetworkClock] = {
    val url = s"$network/clock${forceNtpCheck.map(force => s"?forceNtpCheck=$force").getOrElse("")}"
    BccApiRequest(
      HttpRequest(
        uri = url,
        method = GET
      ),
      _.toNetworkClockResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def networkParameters(): BccApiRequest[NetworkParameters] =
    BccApiRequest(
      HttpRequest(
        uri = s"${network}/parameters",
        method = GET
      ),
      _.toNetworkParametersResponse
    )

  private def prepareCreateRestoreRequest[T](
    body: T
  )(implicit m: Marshaller[T, RequestEntity]) =
    Marshal(body).to[RequestEntity].map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$wallets",
          method = POST,
          entity = marshalled
        ),
        _.toWallet
      )
    }

  /**
   * @inheritdoc
   */
  override def createRestoreWallet(name: String,
                                   passphrase: String,
                                   mnemonicSentence: MnemonicSentence,
                                   mnemonicSecondFactor: Option[MnemonicSentence] = None,
                                   addressPoolGap: Option[Int] = None
  ): Future[BccApiRequest[Wallet]] =
    prepareCreateRestoreRequest(
      CreateRestore(
        name,
        passphrase,
        mnemonicSentence.mnemonicSentence,
        mnemonicSecondFactor.map(_.mnemonicSentence),
        addressPoolGap
      )
    )

  /**
   * @inheritdoc
   */
  override def createRestoreWalletWithKey(name: String,
                                          accountPublicKey: String,
                                          addressPoolGap: Option[Int] = None
  ): Future[BccApiRequest[Wallet]] =
    prepareCreateRestoreRequest(
      CreateRestoreWithKey(
        name,
        accountPublicKey,
        addressPoolGap
      )
    )

  /**
   * @inheritdoc
   */
  override def listAddresses(walletId: String,
                             state: Option[AddressFilter]
  ): BccApiRequest[Seq[WalletAddressId]] = {

    val baseUri = Uri(s"$wallets/${walletId}/addresses")

    val url = state.map { s =>
      baseUri.withQuery(Query("state" -> s.toString))
    }.getOrElse(baseUri)

    BccApiRequest(
      HttpRequest(
        uri = url,
        method = GET
      ),
      _.toWalletAddressIds
    )

  }

  /**
   * @inheritdoc
   */
  override def inspectAddress(addressId: String): BccApiRequest[WalletAddress] = {
    val url = Uri(s"$addresses/$addressId")

    BccApiRequest(
      HttpRequest(
        uri = url,
        method = GET
      ),
      _.toWalletAddress
    )
  }

  /**
   * @inheritdoc
   */
  override def listTransactions(walletId: String,
                                start: Option[ZonedDateTime] = None,
                                end: Option[ZonedDateTime] = None,
                                order: Order = Order.descendingOrder,
                                minWithdrawal: Option[Int] = None
  ): BccApiRequest[Seq[CreateTransactionResponse]] = {
    val baseUri = Uri(s"$wallets/${walletId}/transactions")

    val queries =
      Seq("start", "end", "order", "minWithdrawal").zip(Seq(start, end, order, minWithdrawal)).collect {
        case (queryParamName, order: Order)            => queryParamName -> order.toString
        case (queryParamName, Some(dt: ZonedDateTime)) => queryParamName -> zonedDateToString(dt)
        case (queryParamName, Some(minWith: Int))      => queryParamName -> minWith.toString
      }

    val uriWithQueries = baseUri.withQuery(Query(queries: _*))

    BccApiRequest(
      HttpRequest(
        uri = uriWithQueries,
        method = GET
      ),
      _.toCreateTransactionsResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def createTransaction(fromWalletId: String,
                                 passphrase: String,
                                 payments: Payments,
                                 metadata: Option[TxMetadataIn],
                                 withdrawal: Option[String]
  ): Future[BccApiRequest[CreateTransactionResponse]] = {

    val createTx = CreateTransaction(passphrase, payments.payments, metadata, withdrawal)

    Marshal(createTx).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$wallets/$fromWalletId/transactions",
          method = POST,
          entity = marshalled
        ),
        _.toCreateTransactionResponse
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def estimateFee(fromWalletId: String,
                           payments: Payments,
                           withdrawal: Option[String],
                           metadataIn: Option[TxMetadataIn] = None
  ): Future[BccApiRequest[EstimateFeeResponse]] = {

    val estimateFees = EstimateFee(payments.payments, withdrawal, metadataIn)

    Marshal(estimateFees).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$wallets/$fromWalletId/payment-fees",
          method = POST,
          entity = marshalled
        ),
        _.toEstimateFeeResponse
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def fundPayments(walletId: String,
                            payments: Payments
  ): Future[BccApiRequest[FundPaymentsResponse]] =
    Marshal(payments).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$wallets/${walletId}/coin-selections/random",
          method = POST,
          entity = marshalled
        ),
        _.toFundPaymentsResponse
      )
    }

  /**
   * @inheritdoc
   */
  override def getTransaction[T <: TxMetadataIn](walletId: String,
                                                 transactionId: String
  ): BccApiRequest[CreateTransactionResponse] = {

    val uri = Uri(s"$wallets/${walletId}/transactions/${transactionId}")

    BccApiRequest(
      HttpRequest(
        uri = uri,
        method = GET
      ),
      _.toCreateTransactionResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def deleteTransaction(walletId: String, transactionId: String): BccApiRequest[Unit] = {
    val uri = Uri(s"$wallets/${walletId}/transactions/${transactionId}")

    BccApiRequest(
      HttpRequest(
        uri = uri,
        method = DELETE
      ),
      _.toUnit
    )
  }

  /**
   * @inheritdoc
   */
  override def updatePassphrase(walletId: String,
                                oldPassphrase: String,
                                newPassphrase: String
  ): Future[BccApiRequest[Unit]] = {

    val uri = Uri(s"$wallets/${walletId}/passphrase")
    val updater = UpdatePassphrase(oldPassphrase, newPassphrase)

    Marshal(updater).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = uri,
          method = PUT,
          entity = marshalled
        ),
        _.toUnit
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def deleteWallet(
    walletId: String
  ): BccApiRequest[Unit] = {

    val uri = Uri(s"$wallets/${walletId}")

    BccApiRequest(
      HttpRequest(
        uri = uri,
        method = DELETE
      ),
      _.toUnit
    )
  }

  /**
   * @inheritdoc
   */
  override def getUTxOsStatistics(walletId: String): BccApiRequest[UTxOStatistics] = {
    val uri = Uri(s"$wallets/$walletId/statistics/utxos")
    BccApiRequest(
      HttpRequest(
        uri = uri,
        method = GET
      ),
      _.toUTxOStatisticsResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def postExternalTransaction(binary: String): BccApiRequest[PostExternalTransactionResponse] = {
    val uri = Uri(s"$proxy/transactions")
    BccApiRequest(
      HttpRequest(
        uri = uri,
        method = POST,
        entity = HttpEntity(binary).withContentType(ContentTypes.`application/octet-stream`)
      ),
      _.toPostExternalTransactionResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def migrateSophieWallet(walletId: String,
                                    passphrase: String,
                                    addresses: Seq[String]
  ): Future[BccApiRequest[Seq[MigrationResponse]]] = {
    val updater = SubmitMigration(passphrase = passphrase, addresses = addresses)
    Marshal(updater).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = generateMigrationsUrl(walletId),
          method = POST,
          entity = marshalled
        ),
        _.toSubmitMigrationsResponse
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def getSophieWalletMigrationInfo(walletId: String): BccApiRequest[MigrationCostResponse] =
    BccApiRequest(
      HttpRequest(
        uri = generateMigrationsUrl(walletId),
        method = GET
      ),
      _.toMigrationCostResponse
    )

  /**
   * @inheritdoc
   */
  override def listStakePools(stake: Int): BccApiRequest[Seq[StakePool]] = {
    val url = Uri(s"$stakePools?stake=$stake")
    BccApiRequest(
      HttpRequest(
        uri = url,
        method = GET
      ),
      _.toStakePoolsResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def estimateFeeStakePool(walletId: String): BccApiRequest[EstimateFeeResponse] = {
    val url = Uri(s"$wallets/$walletId/delegation-fees")
    BccApiRequest(
      HttpRequest(
        uri = url,
        method = GET
      ),
      _.toEstimateFeeResponse
    )
  }

  /**
   * @inheritdoc
   */
  override def joinStakePool(walletId: String,
                             stakePoolId: String,
                             passphrase: String
  ): Future[BccApiRequest[MigrationResponse]] = {
    val updater = PassphraseRequest(passphrase = passphrase)
    Marshal(updater).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$stakePools/$stakePoolId/wallets/$walletId",
          method = PUT,
          entity = marshalled
        ),
        _.toSubmitMigrationResponse
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def quitStakePool(walletId: String,
                             passphrase: String
  ): Future[BccApiRequest[MigrationResponse]] = {
    val updater = PassphraseRequest(passphrase = passphrase)
    Marshal(updater).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$stakePools/*/wallets/$walletId",
          method = DELETE,
          entity = marshalled
        ),
        _.toSubmitMigrationResponse
      )
    }
  }

  /**
   * @inheritdoc
   */
  override def getMaintenanceActions(): BccApiRequest[StakePoolMaintenanceActionsStatus] =
    BccApiRequest(
      HttpRequest(
        uri = s"$stakePools/maintenance-actions",
        method = GET
      ),
      _.toStakePoolMaintenanceActionsStatusResponse
    )

  /**
   * @inheritdoc
   */
  override def postMaintenanceAction(): Future[BccApiRequest[Unit]] = {
    val action = PostMaintenanceActionRequest("gc_stake_pools")
    Marshal(action).to[RequestEntity] map { marshalled =>
      BccApiRequest(
        HttpRequest(
          uri = s"$stakePools/maintenance-actions",
          method = POST,
          entity = marshalled
        ),
        _.toUnit
      )
    }
  }

}
