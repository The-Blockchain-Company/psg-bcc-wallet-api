package iog.psg.bcc

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import iog.psg.bcc.BccApi.Order.Order

import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.concurrent.{ Await, ExecutionContext, Future }

/**
 * Defines the API which wraps the Bcc API, depends on BccApiCodec for it's implementation,
 * so clients will import the Codec also.
 */
object BccApi {

  def apply(baseUriWithPort: String)(implicit ec: ExecutionContext, as: ActorSystem): BccApi =
    new BccApiImpl(baseUriWithPort)

  implicit val defaultMaxWaitTime: FiniteDuration = 15.seconds

  type BccApiResponse[T] = Either[ErrorMessage, T]

  final case class ErrorMessage(message: String, code: String)
  final case class BccApiRequest[T](request: HttpRequest,
                                        mapper: HttpResponse => Future[BccApiResponse[T]]
  )

  object Order extends Enumeration {
    type Order = Value
    val ascendingOrder = Value("ascending")
    val descendingOrder = Value("descending")
  }

  object BccApiOps {

    implicit class FlattenOp[T](val knot: Future[BccApiResponse[Future[BccApiResponse[T]]]])
        extends AnyVal {

      def flattenBccApiResponse(implicit ec: ExecutionContext): Future[BccApiResponse[T]] =
        knot.flatMap {
          case Left(errorMessage) => Future.successful(Left(errorMessage))
          case Right(vaue)        => vaue
        }
    }

    implicit class FutOp[T](val request: BccApiRequest[T]) extends AnyVal {
      def toFuture: Future[BccApiRequest[T]] = Future.successful(request)
    }

    //tie execute to ioEc
    implicit class BccApiRequestFOps[T](requestF: Future[BccApiRequest[T]])(implicit
      executor: ApiRequestExecutor,
      ec: ExecutionContext,
      as: ActorSystem
    ) {
      def execute: Future[BccApiResponse[T]] =
        requestF.flatMap(_.execute)

      def executeBlocking(implicit maxWaitTime: Duration): BccApiResponse[T] =
        Await.result(execute, maxWaitTime)

    }

    implicit class BccApiRequestOps[T](request: BccApiRequest[T])(implicit
      executor: ApiRequestExecutor,
      ec: ExecutionContext,
      as: ActorSystem
    ) {

      def execute: Future[BccApiResponse[T]] = executor.execute(request)

      def executeBlocking(implicit maxWaitTime: Duration): BccApiResponse[T] =
        Await.result(execute, maxWaitTime)
    }

  }

}

trait BccApi {

  import BccApiCodec._
  import AddressFilter.AddressFilter
  import iog.psg.bcc.BccApi._

  /**
   * List of known wallets, ordered from oldest to newest.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listWallets #listWallets]]
   *
   * @return list wallets request
   */
  def listWallets: BccApiRequest[Seq[Wallet]]

  /**
   * Get wallet details by id
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getWallet #getWallet]]
   *
   * @param walletId wallet's id
   * @return get wallet request
   */
  def getWallet(walletId: String): BccApiRequest[Wallet]

  /**
   * Update wallet's name
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/putWallet #putWallet]]
   *
   * @param walletId wallet's id
   * @param name new wallet's name
   * @return update wallet request
   */
  def updateName(walletId: String, name: String): Future[BccApiRequest[Wallet]]

  /**
   * Gives network information
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkInformation #getNetworkInformation]]
   *
   * @return network info request
   */
  def networkInfo: BccApiRequest[NetworkInfo]

  /**
   * Gives network clock information
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkClock #getNetworkClock]]
   *
   * @param forceNtpCheck When this flag is set, the request will block until NTP server responds or will timeout after a while without any answer from the NTP server.
   * @return network clock info request
   */
  def networkClock(forceNtpCheck: Option[Boolean] = None): BccApiRequest[NetworkClock]

  /**
   * Gives network parameters
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getNetworkParameters #getNetworkParameters]]
   *
   * @return network parameters request
   */
  def networkParameters(): BccApiRequest[NetworkParameters]

  /**
   * Create and restore a wallet from a mnemonic sentence or account public key.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postWallet #postWallet]]
   *
   * @param name wallet's name
   * @param passphrase A master passphrase to lock and protect the wallet for sensitive operation (e.g. sending funds)
   * @param mnemonicSentence A list of mnemonic words [ 15 .. 24 ] items ( can be generated using https://iancoleman.io/bip39 )
   * @param mnemonicSecondFactor An optional passphrase used to encrypt the mnemonic sentence. [ 9 .. 12 ] items
   * @param addressPoolGap An optional number of consecutive unused addresses allowed
   * @return create/restore wallet request
   */
  def createRestoreWallet(name: String,
                          passphrase: String,
                          mnemonicSentence: MnemonicSentence,
                          mnemonicSecondFactor: Option[MnemonicSentence] = None,
                          addressPoolGap: Option[Int] = None
  ): Future[BccApiRequest[Wallet]]

  /**
   * Create and restore a wallet from a mnemonic sentence or account public key.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postWallet #postWallet]]
   *
   * @param name wallet's name
   * @param accountPublicKey An extended account public key (public key + chain code)
   * @param addressPoolGap An optional number of consecutive unused addresses allowed
   * @return create/restore wallet request
   */
  def createRestoreWalletWithKey(name: String,
                          accountPublicKey: String,
                          addressPoolGap: Option[Int] = None
                         ): Future[BccApiRequest[Wallet]]

  /**
   * List of known addresses, ordered from newest to oldest
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Addresses #Addresses]]
   *
   * @param walletId wallet's id
   * @param state addresses state: used, unused
   * @return list wallet addresses request
   */
  def listAddresses(walletId: String, state: Option[AddressFilter]): BccApiRequest[Seq[WalletAddressId]]

  /**
   * Give useful information about the structure of a given address.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/inspectAddress #inspectAddress]]
   *
   * @param addressId
   * @return address inspect request
   */
  def inspectAddress(addressId: String): BccApiRequest[WalletAddress]

  /**
   * Lists all incoming and outgoing wallet's transactions.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listTransactions #listTransactions]]
   *
   * @param walletId wallet's id
   * @param start    An optional start time in ISO 8601 date-and-time format. Basic and extended formats are both accepted. Times can be local (with a timezone offset) or UTC.
   *                 If both a start time and an end time are specified, then the start time must not be later than the end time.
   *                 Example: 2008-08-08T08:08:08Z
   * @param end      An optional end time in ISO 8601 date-and-time format. Basic and extended formats are both accepted. Times can be local (with a timezone offset) or UTC.
   *                 If both a start time and an end time are specified, then the start time must not be later than the end time.
   *                 Example: 2008-08-08T08:08:08Z
   * @param order    Default: "descending" ( "ascending", "descending" )
   * @param minWithdrawal Returns only transactions that have at least one withdrawal above the given amount.
   *                      This is particularly useful when set to 1 in order to list the withdrawal history of a wallet.
   * @return list wallet's transactions request
   */
  def listTransactions(walletId: String,
                       start: Option[ZonedDateTime] = None,
                       end: Option[ZonedDateTime] = None,
                       order: Order = Order.descendingOrder,
                       minWithdrawal: Option[Int] = None
  ): BccApiRequest[Seq[CreateTransactionResponse]]

  /**
   * Create and send transaction from the wallet.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransaction #postTransaction]]
   *
   * @param fromWalletId wallet's id
   * @param passphrase The wallet's master passphrase. [ 0 .. 255 ] characters
   * @param payments A list of target outputs ( address, amount )
   * @param withdrawal Optional, when provided, instruments the server to automatically withdraw rewards from the source
   *                   wallet when they are deemed sufficient (i.e. they contribute to the balance for at least as much
   *                   as they cost).
   * @param metadata   Extra application data attached to the transaction.
   * @return create transaction request
   */
  def createTransaction(fromWalletId: String,
                        passphrase: String,
                        payments: Payments,
                        metadata: Option[TxMetadataIn],
                        withdrawal: Option[String]
  ): Future[BccApiRequest[CreateTransactionResponse]]

  /**
   * Estimate fee for the transaction. The estimate is made by assembling multiple transactions and analyzing the
   * distribution of their fees. The estimated_max is the highest fee observed, and the estimated_min is the fee which
   * is lower than at least 90% of the fees observed.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postTransactionFee #estimateFee]]
   *
   * @param fromWalletId wallet's id
   * @param payments A list of target outputs ( address, amount )
   * @param withdrawal Optional, when provided, instruments the server to automatically withdraw rewards from the source
   *                   wallet when they are deemed sufficient (i.e. they contribute to the balance for at least as much
   *                   as they cost).
   * @param metadataIn Extra application data attached to the transaction.
   * @return estimate fee request
   */
  def estimateFee(fromWalletId: String,
                  payments: Payments,
                  withdrawal: Option[String],
                  metadataIn: Option[TxMetadataIn] = None
  ): Future[BccApiRequest[EstimateFeeResponse]]

  /**
   * Select coins to cover the given set of payments.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#tag/Coin-Selections #CoinSelections]]
   *
   * @param walletId wallet's id
   * @param payments A list of target outputs ( address, amount )
   * @return fund payments request
   */
  def fundPayments(walletId: String, payments: Payments): Future[BccApiRequest[FundPaymentsResponse]]

  /**
   * Get transaction by id.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getTransaction #getTransaction]]
   *
   * @param walletId wallet's id
   * @param transactionId transaction's id
   * @return get transaction request
   */
  def getTransaction[T <: TxMetadataIn](walletId: String,
                                        transactionId: String
  ): BccApiRequest[CreateTransactionResponse]

  /**
   * Forget pending transaction
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/deleteTransaction #deleteTransaction]]
   *
   * @param walletId wallet's id
   * @param transactionId transaction's id
   * @return forget pending transaction request
   */
  def deleteTransaction(walletId: String, transactionId: String): BccApiRequest[Unit]

  /**
   * Update Passphrase
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/putWalletPassphrase #putWalletPassphrase]]
   * @param walletId wallet's id
   * @param oldPassphrase current passphrase
   * @param newPassphrase new passphrase
   * @return update passphrase request
   */
  def updatePassphrase(walletId: String,
                       oldPassphrase: String,
                       newPassphrase: String
  ): Future[BccApiRequest[Unit]]

  /**
   * Delete wallet by id
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/deleteWallet #deleteWallet]]
   * @param walletId wallet's id
   * @return delete wallet request
   */
  def deleteWallet(walletId: String): BccApiRequest[Unit]

  /**
   * Return the UTxOs distribution across the whole wallet, in the form of a histogram
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getUTxOsStatistics #getUTxOsStatistics]]
   *
   * @param walletId wallet's id
   * @return get UTxOs statistics request
   */
  def getUTxOsStatistics(walletId: String): BccApiRequest[UTxOStatistics]

  /**
   * Submits a transaction that was created and signed outside of bcc-wallet.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postExternalTransaction #postExternalTransaction]]
   *
   * @param binary message binary blob string
   * @return post external transaction request
   */
  def postExternalTransaction(binary: String): BccApiRequest[PostExternalTransactionResponse]

  /**
   * Submit one or more transactions which transfers all funds from a Sophie wallet to a set of addresses
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/migrateSophieWallet #migrateSophieWallet]]
   *
   * @param walletId wallet's id
   * @param passphrase wallet's master passphrase
   * @param addresses recipient addresses
   * @return migrate sophie wallet request
   */
  def migrateSophieWallet(walletId: String,
                           passphrase: String,
                           addresses: Seq[String]
  ): Future[BccApiRequest[Seq[MigrationResponse]]]

  /**
   * Calculate the exact cost of sending all funds from particular Sophie wallet to a set of addresses
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getSophieWalletMigrationInfo #getSophieWalletMigrationInfo]]
   * @param walletId wallet's id
   * @return migration cost request
   */
  def getSophieWalletMigrationInfo(walletId: String): BccApiRequest[MigrationCostResponse]

  /**
   * List all known stake pools ordered by descending non_myopic_member_rewards.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/listStakePools #listStakePools]]
   *
   * @param stake The stake the user intends to delegate in Entropic. Required.
   * @return list stake pools request
   */
  def listStakePools(stake: Int): BccApiRequest[Seq[StakePool]]

  /**
   * Estimate fee for joining or leaving a stake pool
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getDelegationFee #getDelegationFee]]
   *
   * @param walletId wallet's id
   * @return estimate fee request
   */
  def estimateFeeStakePool(walletId: String): BccApiRequest[EstimateFeeResponse]

  /**
   * Delegate all (current and future) addresses from the given wallet to the given stake pool.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/joinStakePool #joinStakePool]]
   *
   * @param walletId wallet's id
   * @param stakePoolId stakePool's id
   * @param passphrase wallet's passphrase
   * @return quit stake pool request
   */
  def joinStakePool(walletId: String,
                    stakePoolId: String,
                    passphrase: String
  ): Future[BccApiRequest[MigrationResponse]]

  /**
   * Stop delegating completely. The wallet's stake will become inactive.
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/quitStakePool #quitStakePool]]
   *
   * @param walletId wallet's id
   * @param passphrase wallet's passphrase
   * @return quit stake pool request
   */
  def quitStakePool(walletId: String, passphrase: String): Future[BccApiRequest[MigrationResponse]]

  /**
   * View maintenance actions
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/getMaintenanceActions #getMaintenanceActions]]
   *
   * @return the current status of the stake pools maintenance actions request
   */
  def getMaintenanceActions(): BccApiRequest[StakePoolMaintenanceActionsStatus]

  /**
   * Trigger Maintenance actions
   * Api Url: [[https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/postMaintenanceAction #postMaintenanceAction]]
   *
   * @return Trigger Maintenance actions request
   */
  def postMaintenanceAction(): Future[BccApiRequest[Unit]]
}
