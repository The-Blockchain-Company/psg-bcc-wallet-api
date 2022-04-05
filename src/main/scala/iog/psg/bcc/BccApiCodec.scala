package iog.psg.bcc

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.ContentType.WithFixedCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller.eitherUnmarshaller
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.extras.{ConfiguredJsonCodec, _}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax.EncoderOps
import iog.psg.bcc.BccApi.{BccApiResponse, ErrorMessage}
import iog.psg.bcc.BccApiCodec.AddressFilter.AddressFilter
import iog.psg.bcc.BccApiCodec.DelegationStatus.DelegationStatus
import iog.psg.bcc.BccApiCodec.SyncState.SyncState
import iog.psg.bcc.BccApiCodec.TxDirection.TxDirection
import iog.psg.bcc.BccApiCodec.TxState.TxState
import iog.psg.bcc.BccApiCodec.Units.Units
import org.apache.commons.codec.binary.Hex

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object BccApiCodec {

  object ImplicitCodecs {
    implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

    implicit val createRestoreEntityEncoder: Encoder[CreateRestore] = dropNulls(deriveConfiguredEncoder)
    implicit val createRestoreWithKeyEntityEncoder: Encoder[CreateRestoreWithKey] = dropNulls(deriveConfiguredEncoder)
    implicit val createListAddrEntityEncoder: Encoder[WalletAddressId] = dropNulls(deriveConfiguredEncoder)

    implicit val decodeDateTime: Decoder[ZonedDateTime] = Decoder.decodeString.emap { s =>
      stringToZonedDate(s) match {
        case Success(goodDateTime) => Right(goodDateTime)
        case Failure(exception) => Left(exception.toString)
      }
    }

    implicit val decodeUnits: Decoder[Units] = Decoder.decodeString.map(Units.withName)
    implicit val encodeUnits: Encoder[Units] = (a: Units) => Json.fromString(a.toString)

    implicit val decodeSyncState: Decoder[SyncState] = Decoder.decodeString.map(SyncState.withName)
    implicit val encodeSyncState: Encoder[SyncState] = (a: SyncState) => Json.fromString(a.toString)

    implicit val decodeAddressFilter: Decoder[AddressFilter] = Decoder.decodeString.map(AddressFilter.withName)
    implicit val encodeAddressFilter: Encoder[AddressFilter] = (a: AddressFilter) => Json.fromString(a.toString)

    implicit val decodeTxState: Decoder[TxState] = Decoder.decodeString.map(TxState.withName)
    implicit val encodeTxState: Encoder[TxState] = (a: TxState) => Json.fromString(a.toString)

    implicit val decodeTxDirection: Decoder[TxDirection] = Decoder.decodeString.map(TxDirection.withName)
    implicit val encodeTxDirection: Encoder[TxDirection] = (a: TxDirection) => Json.fromString(a.toString)

    implicit val decodeDelegationStatus: Decoder[DelegationStatus] = Decoder.decodeString.map(DelegationStatus.withName)
    implicit val encodeDelegationStatus: Encoder[DelegationStatus] = (a: DelegationStatus) => Json.fromString(a.toString)

    implicit val decodeTxMetadataOut: Decoder[TxMetadataOut] = Decoder.decodeJson.map(TxMetadataOut.apply)
    implicit val decodeKeyMetadata: KeyDecoder[MetadataKey] = (key: String) => Some(MetadataValueStr(key))

    implicit val encodeDelegationNext: Encoder[DelegationNext] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeDelegationActive: Encoder[DelegationActive] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeNetworkTip: Encoder[NetworkTip] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeNodeTip: Encoder[NodeTip] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeSyncStatus: Encoder[SyncStatus] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeCreateTransactionResponse: Encoder[CreateTransactionResponse] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeWallet: Encoder[Wallet] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeBlock: Encoder[Block] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeWalletAddress: Encoder[WalletAddress] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeStakePool: Encoder[StakePool] = dropNulls(deriveConfiguredEncoder)
    implicit val encodeSubmitMigrationResponse: Encoder[MigrationResponse] = dropNulls(deriveConfiguredEncoder)

    private def decodeQuantityUnit[T](c: HCursor)(implicit d: Decoder[T]) = for {
      quantity <- c.downField("quantity").as[T]
      unitsStr <- c.downField("unit").as[String]
      units <- Try(Units.withName(unitsStr)).toEither.left.map(_ => DecodingFailure("unit", c.history))
    } yield {
      QuantityUnit(quantity, units)
    }

    implicit val decodeQuantityUnitL: Decoder[QuantityUnit[Long]] =
      (c: HCursor) => decodeQuantityUnit[Long](c)

    implicit val decodeQuantityUnitD: Decoder[QuantityUnit[Double]] =
      (c: HCursor) => decodeQuantityUnit[Double](c)


    implicit val metadataKeyDecoder: KeyEncoder[MetadataKey] = {
      case MetadataValueLong(l) => l.toString
      case MetadataValueStr(s) => s
    }


    implicit val encodeTxMeta: Encoder[MetadataValue] = Encoder.instance {
      case MetadataValueLong(s) => Json.obj(("int", Json.fromLong(s)))
      case MetadataValueStr(s) => Json.obj(("string", Json.fromString(s)))
      case MetadataValueByteString(bs: ByteString) => Json.obj(("bytes", Json.fromString(bs.utf8String)))
      case MetadataValueArray(s) => Json.obj(("list", s.asJson))
      case MetadataValueMap(s) =>
        Json.obj(("map", s.map {
          case (key, value) => Map("k" -> key, "v" -> value)
        }.asJson))
    }

    implicit val encodeTxMetadata: Encoder[TxMetadataIn] = Encoder.instance {
      case JsonMetadata(metadataCompliantJson) => metadataCompliantJson
      case TxMetadataMapIn(s) => s.asJson
    }
  }

  import ImplicitCodecs._

  def dropNulls[A](encoder: Encoder[A]): Encoder[A] =
    encoder.mapJson(_.dropNullValues)

  def toMetadataHex(bs: ByteString): Json = {
    val asHex = Hex.encodeHex(bs.toArray[Byte])
    asHex.asJson
  }

  sealed trait MetadataValue

  sealed trait MetadataKey extends MetadataValue

  sealed trait TxMetadataIn

  final case class TxMetadataMapIn[K <: Long](m: Map[K, MetadataValue]) extends TxMetadataIn

  object JsonMetadata {
    def apply(rawJson: String): JsonMetadata = parse(rawJson) match {
      case Left(p: ParsingFailure) => throw p
      case Right(jsonMeta) => jsonMeta
    }

    def parse(rawJson: String): Either[ParsingFailure, JsonMetadata] = parser.parse(rawJson).map(JsonMetadata(_))
  }

  final case class JsonMetadata(metadataCompliantJson: Json) extends TxMetadataIn

  final case class MetadataValueLong(l: Long) extends MetadataKey

  final case class MetadataValueStr(s: String) extends MetadataKey

  final case class MetadataValueArray(ary: Seq[MetadataValue]) extends MetadataValue

  final case class MetadataValueByteString(bs: ByteString) extends MetadataValue

  final case class MetadataValueMap(s: Map[MetadataKey, MetadataValue]) extends MetadataValue


  object AddressFilter extends Enumeration {
    type AddressFilter = Value

    val used = Value("used")
    val unUsed = Value("unused")

  }

  final case class SyncStatus(status: SyncState, progress: Option[QuantityUnit[Double]])

  object SyncState extends Enumeration {
    type SyncState = Value
    val ready: SyncState = Value("ready")
    val syncing: SyncState = Value("syncing")
    val notResponding: SyncState = Value("not_responding")

  }

  object DelegationStatus extends Enumeration {
    type DelegationStatus = Value
    val delegating: DelegationStatus = Value("delegating")
    val notDelegating: DelegationStatus = Value("not_delegating")
  }
  @ConfiguredJsonCodec(decodeOnly = true) final case class DelegationActive(status: DelegationStatus, target: Option[String])
  @ConfiguredJsonCodec(decodeOnly = true) final case class DelegationNext(status: DelegationStatus, changesAt: Option[NextEpoch])
  @ConfiguredJsonCodec final case class Delegation(active: DelegationActive, next: List[DelegationNext])

  @ConfiguredJsonCodec(decodeOnly = true) final case class NetworkTip(
                                              epochNumber: Long,
                                              slotNumber: Long,
                                              height: Option[QuantityUnit[Long]],
                                              absoluteSlotNumber: Option[Long])

  @ConfiguredJsonCodec(decodeOnly = true) final case class NodeTip(height: QuantityUnit[Long], slotNumber: Long, epochNumber: Long, absoluteSlotNumber: Option[Long])

  case class WalletAddressId(id: String, state: Option[AddressFilter])

  @ConfiguredJsonCodec(decodeOnly = true) final case class Pointer(slotNum: Long, transactionIndex: Long, outputIndex: Long)

  @ConfiguredJsonCodec(decodeOnly = true) final case class WalletAddress(
                                                                          addressStyle: String,
                                                                          stakeReference: String,
                                                                          networkTag: Long,
                                                                          spendingKeyHash: String,
                                                                          stakeKeyHash: String,
                                                                          scriptHash: Option[String],
                                                                          pointer: Option[Pointer],
                                                                          addressRoot: Option[String],
                                                                          derivationPath: Option[String])

  private[bcc] case class CreateTransaction(
                                                 passphrase: String,
                                                 payments: Seq[Payment],
                                                 metadata: Option[TxMetadataIn],
                                                 withdrawal: Option[String])

  private[bcc] case class EstimateFee(payments: Seq[Payment], withdrawal: Option[String], metadata: Option[TxMetadataIn])

  case class Payments(payments: Seq[Payment])

  case class Payment(address: String, amount: QuantityUnit[Long])

  @ConfiguredJsonCodec private[bcc] case class UpdatePassphrase(oldPassphrase: String, newPassphrase: String)

  trait MnemonicSentence {
    val mnemonicSentence: IndexedSeq[String]
  }

  case class GenericMnemonicSentence(mnemonicSentence: IndexedSeq[String]) extends MnemonicSentence {
    require(
      mnemonicSentence.length == 15 ||
        mnemonicSentence.length == 21 ||
        mnemonicSentence.length == 24, s"Mnemonic word list must be 15, 21, or 24 long (not ${mnemonicSentence.length})")
  }

  object GenericMnemonicSentence {
    def apply(mnemonicString: String): GenericMnemonicSentence =
      GenericMnemonicSentence(mnemonicString.split(" ").toIndexedSeq)
  }

  final case class GenericMnemonicSecondaryFactor(mnemonicSentence: IndexedSeq[String]) extends MnemonicSentence {
    require(
      mnemonicSentence.length == 9 ||
        mnemonicSentence.length == 12, s"Mnemonic word list must be 9, 12 long (not ${mnemonicSentence.length})")
  }

  object GenericMnemonicSecondaryFactor {
    def apply(mnemonicSentence: String): GenericMnemonicSecondaryFactor =
      GenericMnemonicSecondaryFactor(mnemonicSentence.split(" ").toIndexedSeq)
  }

  @ConfiguredJsonCodec
  case class NextEpoch(epochStartTime: ZonedDateTime, epochNumber: Long)

  @ConfiguredJsonCodec
  case class NetworkInfo(
                          syncProgress: SyncStatus,
                          networkTip: Option[NetworkTip],
                          nodeTip: NodeTip,
                          nextEpoch: Option[NextEpoch]
                        )

  @ConfiguredJsonCodec
  final case class NetworkClock(
                                 status: String,
                                 offset: QuantityUnit[Long]
                               )

  @ConfiguredJsonCodec
  final case class NetworkParameters(
                                      genesisBlockHash: String,
                                      blockchain_start_time: ZonedDateTime,
                                      slotLength: QuantityUnit[Long],
                                      epochLength: QuantityUnit[Long],
                                      @deprecated("'epoch stability' is removed from API in v2021.4.28")
                                      epochStability: Option[QuantityUnit[Long]],
                                      activeSlotCoefficient: QuantityUnit[Long],
                                      decentralizationLevel: QuantityUnit[Long],
                                      desiredPoolNumber: Long,
                                      minimumUtxoValue: QuantityUnit[Long],
                                      @deprecated("'hard fork at' is removed from API in v2021.4.28")
                                      hardforkAt: Option[NextEpoch]
                                    )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class CreateRestore(
                            name: String,
                            passphrase: String,
                            mnemonicSentence: IndexedSeq[String],
                            mnemonicSecondFactor: Option[IndexedSeq[String]] = None,
                            addressPoolGap: Option[Int] = None
                          ) {
    require(
      mnemonicSentence.length == 15 ||
        mnemonicSentence.length == 21 ||
        mnemonicSentence.length == 24, s"Mnemonic word list must be 15, 21, or 24 long (not ${mnemonicSentence.length})")

    private lazy val mnemonicSecondFactorLength = mnemonicSecondFactor.map(_.length).getOrElse(0)
    require(
      mnemonicSecondFactor.isEmpty || (mnemonicSecondFactorLength == 9 || mnemonicSecondFactorLength == 12)
    )
  }

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class CreateRestoreWithKey(name: String, accountPublicKey: String, addressPoolGap: Option[Int])

  object Units extends Enumeration {
    type Units = Value
    val percent = Value("percent")
    val entropic = Value("entropic")
    val block = Value("block")
    val slot = Value("slot")
    val microsecond = Value("microsecond")
    val second = Value("second")
  }


  object TxDirection extends Enumeration {
    type TxDirection = Value
    val outgoing = Value("outgoing")
    val incoming = Value("incoming")
  }

  object TxState extends Enumeration {
    type TxState = Value
    val pending = Value("pending")
    val inLedger = Value("in_ledger")
    val expired = Value("expired")
    val submitted = Value("submitted")
  }

  @ConfiguredJsonCodec(encodeOnly = true) final case class QuantityUnit[T] private(quantity: T, unit: Units)

  object QuantityUnit {
    def apply(quantity: Long, unit: Units): QuantityUnit[Long] = new QuantityUnit(quantity, unit)
    def apply(quantity: Double, unit: Units): QuantityUnit[Double] = new QuantityUnit(quantity, unit)
  }

  case class Balance(
                      available: QuantityUnit[Long],
                      reward: QuantityUnit[Long],
                      total: QuantityUnit[Long]
                    )

  case class InAddress(
                        address: Option[String],
                        amount: Option[QuantityUnit[Long]],
                        id: String,
                        index: Int)

  case class OutAddress(
                         address: String,
                         amount: QuantityUnit[Long]
                       )

  @ConfiguredJsonCodec
  case class StakeAddress(
                           stakeAddress: String,
                           amount: QuantityUnit[Long]
                         )

  case class FundPaymentsResponse(
                                   inputs: IndexedSeq[InAddress],
                                   outputs: Seq[OutAddress]
                                 )

  @ConfiguredJsonCodec(decodeOnly = true)
  case class Block(
                    slotNumber: Int,
                    epochNumber: Int,
                    height: QuantityUnit[Long],
                    absoluteSlotNumber: Option[Long]
                  )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class TimedBlock(
                               time: ZonedDateTime,
                               @deprecated("block parameter is removed from API since v2020-11-17")
                               block: Option[Block],
                               slotNumber: Option[Int],
                               epochNumber: Option[Int],
                               height: Option[QuantityUnit[Long]],
                               absoluteSlotNumber: Option[Long]
                             )

  object TimedBlock {
    def apply(time: ZonedDateTime, block: Block): TimedBlock =
      TimedBlock(time = time,
        block = Some(block),
        slotNumber = Some(block.slotNumber),
        epochNumber = Some(block.epochNumber),
        height = Some(block.height),
        absoluteSlotNumber = block.absoluteSlotNumber
      )
  }

  @ConfiguredJsonCodec
  final case class TimedFlattenBlock(
                                      time: ZonedDateTime,
                                      slotNumber: Int,
                                      epochNumber: Int,
                                      height: Option[QuantityUnit[Long]],
                                      absoluteSlotNumber: Option[Long]
                                    )

  @ConfiguredJsonCodec
  case class EstimateFeeResponse(
                                  estimatedMin: QuantityUnit[Long],
                                  estimatedMax: QuantityUnit[Long]
                                )

  @ConfiguredJsonCodec(decodeOnly = true)
  case class CreateTransactionResponse(
                                        id: String,
                                        amount: QuantityUnit[Long],
                                        insertedAt: Option[TimedBlock],
                                        pendingSince: Option[TimedBlock],
                                        depth: Option[QuantityUnit[Long]],
                                        direction: TxDirection,
                                        inputs: Seq[InAddress],
                                        outputs: Seq[OutAddress],
                                        withdrawals: Seq[StakeAddress],
                                        status: TxState,
                                        metadata: Option[TxMetadataOut]
                                      )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class MigrationResponse(
                                        id: String,
                                        amount: QuantityUnit[Long],
                                        insertedAt: Option[TimedBlock],
                                        pendingSince: Option[TimedBlock],
                                        expiresAt: Option[TimedBlock],
                                        depth: Option[QuantityUnit[Long]],
                                        direction: TxDirection,
                                        inputs: Seq[InAddress],
                                        outputs: Seq[OutAddress],
                                        withdrawals: Seq[StakeAddress],
                                        status: TxState,
                                        metadata: Option[TxMetadataOut]
                                      )

  @ConfiguredJsonCodec
  final case class MigrationCostResponse(migrationCost: QuantityUnit[Long], leftovers: QuantityUnit[Long])

  @ConfiguredJsonCodec
  final case class Passphrase(lastUpdatedAt: ZonedDateTime)

  @ConfiguredJsonCodec(decodeOnly = true)
  case class Wallet(
                     id: String,
                     addressPoolGap: Int,
                     balance: Balance,
                     delegation: Option[Delegation],
                     name: String,
                     passphrase: Option[Passphrase],
                     state: SyncStatus,
                     tip: NetworkTip
                   )

  @ConfiguredJsonCodec
  final case class UTxOStatistics(total: QuantityUnit[Long], scale: String, distribution: Map[String, Long])

  @ConfiguredJsonCodec
  final case class PostExternalTransactionResponse(id: String)

  @ConfiguredJsonCodec(encodeOnly = true)
  final case class SubmitMigration(passphrase: String, addresses: Seq[String])

  @ConfiguredJsonCodec
  final case class StakePoolMetric(
                                    nonMyopicMemberRewards: QuantityUnit[Long],
                                    relativeStake: QuantityUnit[Double],
                                    saturation: Double,
                                    producedBlocks: QuantityUnit[Long]
                                  )

  @ConfiguredJsonCodec
  final case class StakePoolMetadata(
                                      ticker: String,
                                      name: String,
                                      description: String,
                                      homepage: String
                                    )

  @ConfiguredJsonCodec(decodeOnly = true)
  final case class StakePool(
                              id: String,
                              metrics: StakePoolMetric,
                              cost: QuantityUnit[Long],
                              margin: QuantityUnit[Double],
                              pledge: QuantityUnit[Long],
                              metadata: Option[StakePoolMetadata],
                              retirement: Option[NextEpoch]
                            )

  @ConfiguredJsonCodec
  final case class GcStakePools(status: String, lastRun: ZonedDateTime)

  @ConfiguredJsonCodec
  final case class StakePoolMaintenanceActionsStatus(gcStakePools: GcStakePools)

  @ConfiguredJsonCodec(encodeOnly = true)
  final case class PassphraseRequest(passphrase: String)

  @ConfiguredJsonCodec(encodeOnly = true)
  final case class PostMaintenanceActionRequest(maintenanceAction: String)

  def stringToZonedDate(dateAsString: String): Try[ZonedDateTime] = {
    Try(ZonedDateTime.parse(dateAsString, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  }

  def zonedDateToString(zonedDateTime: ZonedDateTime): String = {
    zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }

  def toErrorMessage(bs: ByteString): Either[io.circe.Error, ErrorMessage] = {
    import io.circe.parser.decode
    decode[ErrorMessage](bs.utf8String)
  }

  implicit class ResponseOps(response: HttpResponse)(
    implicit mat: Materializer,
    timeout: FiniteDuration,
    ec: ExecutionContext) {

    private def strictEntityF: Future[HttpEntity.Strict] = response.entity.toStrict(timeout)

    private def errorUnparseableResult[T](err: Throwable): BccApiResponse[T] = Left(ErrorMessage(err.getMessage, "UNPARSEABLE RESULT"))

    private def extractErrorResponse[T](strictEntity: Future[HttpEntity.Strict]): Future[BccApiResponse[T]] =
      strictEntity.map(e => toErrorMessage(e.data) match {
        case Left(err) => errorUnparseableResult(err)
        case Right(v) => Left(v)
      })

    def toNetworkInfoResponse: Future[BccApiResponse[NetworkInfo]]
    = to[NetworkInfo](Unmarshal(_).to[BccApiResponse[NetworkInfo]])

    def toNetworkClockResponse: Future[BccApiResponse[NetworkClock]]
    = to[NetworkClock](Unmarshal(_).to[BccApiResponse[NetworkClock]])

    def toNetworkParametersResponse: Future[BccApiResponse[NetworkParameters]]
    = to[NetworkParameters](Unmarshal(_).to[BccApiResponse[NetworkParameters]])

    def to[T](f: HttpEntity.Strict => Future[BccApiResponse[T]]): Future[BccApiResponse[T]] =
      decodeResponseEntityOrHandleError(response, () => strictEntityF.flatMap(f))

    def toWallet: Future[BccApiResponse[Wallet]]
    = to[Wallet](Unmarshal(_).to[BccApiResponse[Wallet]])

    def toWallets: Future[BccApiResponse[Seq[Wallet]]]
    = to[Seq[Wallet]](Unmarshal(_).to[BccApiResponse[Seq[Wallet]]])

    def toWalletAddressIds: Future[BccApiResponse[Seq[WalletAddressId]]]
    = to[Seq[WalletAddressId]](Unmarshal(_).to[BccApiResponse[Seq[WalletAddressId]]])

    def toWalletAddress: Future[BccApiResponse[WalletAddress]]
    = to[WalletAddress](Unmarshal(_).to[BccApiResponse[WalletAddress]])

    def toFundPaymentsResponse: Future[BccApiResponse[FundPaymentsResponse]]
    = to[FundPaymentsResponse](Unmarshal(_).to[BccApiResponse[FundPaymentsResponse]])

    def toCreateTransactionsResponse: Future[BccApiResponse[Seq[CreateTransactionResponse]]]
    = decodeInStream[CreateTransactionResponse](response, "$[*]")

    private def decodeResponseEntityOrHandleError[T](response: HttpResponse, decodeF: () => Future[BccApiResponse[T]]) = {
      response.entity.contentType match {
        case WithFixedCharset(MediaTypes.`application/json`) =>
          decodeF()

        case c: ContentType
          if c.mediaType == MediaTypes.`text/plain` ||
            c.mediaType == MediaTypes.`application/octet-stream` =>

          extractErrorResponse[T](strictEntityF)

        case c: ContentType =>
          Future.failed(new RuntimeException(s"Unexpected type ${c.mediaType}, ${c.charsetOption}"))
      }
    }

    final def decodeInStream[T](response: HttpResponse, jsonPath: String)(implicit um: Unmarshaller[String, T]): Future[BccApiResponse[Seq[T]]] =
      decodeResponseEntityOrHandleError(response, () =>
        response.entity.dataBytes
          .via(JsonReader.select(jsonPath))
          .mapAsync(parallelism = 4)(bs => unmarshalOrRecoverToUnparseable[T](bs.utf8String))
          .runWith(Sink.seq).map(sequenceBccApiResponses)
      )

    private def unmarshalOrRecoverToUnparseable[T](utf8String: String)(implicit um: Unmarshaller[String, T]): Future[BccApiResponse[T]] =
      Unmarshal(utf8String).to[T].map(Right(_)).recover {
        case e: Exception => errorUnparseableResult(e)
      }

    private def sequenceBccApiResponses[T](responses: Seq[BccApiResponse[T]]): BccApiResponse[Seq[T]] =
      responses.foldLeft[BccApiResponse[Seq[T]]](Right(Seq.empty)) {
        case (_, Left(error)) => Left(error)
        case (acc, Right(elem)) => acc.map(_ :+ elem)
      }

    def toCreateTransactionResponse: Future[BccApiResponse[CreateTransactionResponse]]
    = to[CreateTransactionResponse](Unmarshal(_).to[BccApiResponse[CreateTransactionResponse]])

    def toEstimateFeeResponse: Future[BccApiResponse[EstimateFeeResponse]] =
      to[EstimateFeeResponse](Unmarshal(_).to[BccApiResponse[EstimateFeeResponse]])

    def toUTxOStatisticsResponse: Future[BccApiResponse[UTxOStatistics]] =
      to[UTxOStatistics](Unmarshal(_).to[BccApiResponse[UTxOStatistics]])

    def toPostExternalTransactionResponse: Future[BccApiResponse[PostExternalTransactionResponse]] =
      to[PostExternalTransactionResponse](Unmarshal(_).to[BccApiResponse[PostExternalTransactionResponse]])

    def toSubmitMigrationResponse: Future[BccApiResponse[MigrationResponse]] =
      to[MigrationResponse](Unmarshal(_).to[BccApiResponse[MigrationResponse]])

    def toSubmitMigrationsResponse: Future[BccApiResponse[Seq[MigrationResponse]]] =
      to[Seq[MigrationResponse]](Unmarshal(_).to[BccApiResponse[Seq[MigrationResponse]]])

    def toMigrationCostResponse: Future[BccApiResponse[MigrationCostResponse]] =
      to[MigrationCostResponse](Unmarshal(_).to[BccApiResponse[MigrationCostResponse]])

    def toStakePoolsResponse: Future[BccApiResponse[Seq[StakePool]]] =
      to[Seq[StakePool]](Unmarshal(_).to[BccApiResponse[Seq[StakePool]]])

    def toStakePoolMaintenanceActionsStatusResponse: Future[BccApiResponse[StakePoolMaintenanceActionsStatus]] =
      to[StakePoolMaintenanceActionsStatus](Unmarshal(_).to[BccApiResponse[StakePoolMaintenanceActionsStatus]])

    def toUnit: Future[BccApiResponse[Unit]] = {
      if (response.status == StatusCodes.NoContent) {
        Future.successful(Right(()))
      } else {
        extractErrorResponse[Unit](strictEntityF)
      }
    }

  }


}
