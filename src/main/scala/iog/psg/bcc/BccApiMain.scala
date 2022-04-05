package iog.psg.bcc

import java.io.File
import java.time.ZonedDateTime

import akka.actor.ActorSystem
import iog.psg.bcc.BccApi.BccApiOps.{BccApiRequestFOps, BccApiRequestOps}
import iog.psg.bcc.BccApi.{BccApiResponse, ErrorMessage, Order, defaultMaxWaitTime}
import iog.psg.bcc.BccApiCodec.ImplicitCodecs._
import iog.psg.bcc.BccApiCodec.{AddressFilter, GenericMnemonicSecondaryFactor, GenericMnemonicSentence, Payment, Payments, QuantityUnit, Units, _}
import iog.psg.bcc.util.StringToMetaMapParser.toMetaMap
import iog.psg.bcc.util._

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object BccApiMain {

  object CmdLine {
    //Commands
    val help = "-help"
    val netInfo = "-netInfo"
    val netClockInfo = "-netClockInfo"
    val netParams = "-netParams"
    val listWallets = "-wallets"
    val updateName = "-updateName"
    val deleteWallet = "-deleteWallet"
    val getWallet = "-wallet"
    val createWallet = "-createWallet"
    val createWalletWithKey = "-createWalletWithKey"
    val restoreWallet = "-restoreWallet"
    val restoreWalletWithKey = "-restoreWalletWithKey"
    val estimateFee = "-estimateFee"
    val updatePassphrase = "-updatePassphrase"
    val listWalletAddresses = "-listAddresses"
    val inspectWalletAddress = "-inspectAddress"
    val listWalletTransactions = "-listTxs"
    val createTx = "-createTx"
    val fundTx = "-fundTx"
    val getTx = "-getTx"
    val deleteTx = "-deleteTx"
    val getUTxOsStatistics = "-getUTxO"
    val postExternalTransaction = "-postExternalTransaction"
    val migrateSophieWallet = "-migrateSophieWallet"
    val getSophieWalletMigrationInfo = "-getSophieWalletMigrationInfo"
    val listStakePools = "-listStakePools"
    val estimateFeeStakePool = "-estimateFeeStakePool"
    val joinStakePool = "-joinStakePool"
    val quitStakePool = "-quitStakePool"
    val stakePoolGetMaintenanceActions = "-stakePoolGetMaintenanceActions"
    val stakePoolPostMaintenanceActions = "-stakePoolPostMaintenanceActions"

    //Parameters
    val baseUrl = "-baseUrl"

    val traceToFile = "-trace"
    val noConsole = "-noConsole"

    val forceNtpCheck = "-forceNtpCheck"
    val name = "-name"
    val oldPassphrase = "-oldPassphrase"
    val passphrase = "-passphrase"
    val accountPublicKey = "-accountPublicKey"
    val metadata = "-metadata"
    val mnemonic = "-mnemonic"
    val mnemonicSecondary = "-mnemonicSecondary"
    val addressPoolGap = "-addressPoolGap"
    val state = "-state"
    val walletId = "-walletId"
    val start = "-start"
    val end = "-end"
    val order = "-order"
    val minWithdrawal = "-minWithdrawal"
    val txId = "-txId"
    val amount = "-amount"
    val address = "-address"
    val binary = "-binary"
    val addresses = "-addresses"
    val stake = "-stake"
    val stakePoolId = "-stakePoolId"
  }

  val defaultBaseUrl = "http://127.0.0.1:8090/v2/"
  val defaultTraceFile = "bcc-api.log"

  def main(args: Array[String]): Unit = {

    val arguments = new ArgumentParser(args)
    val helpMode = arguments.contains(CmdLine.help)

    implicit val trace = if (helpMode) {
      ConsoleTrace
    } else {
      val conTracer = if (arguments.contains(CmdLine.noConsole)) NoOpTrace else ConsoleTrace
      conTracer.withTrace(
        if (arguments.contains(CmdLine.traceToFile)) {
          val fileName = arguments(CmdLine.traceToFile).getOrElse(defaultTraceFile)
          new FileTrace(new File(fileName))
        } else NoOpTrace
      )
    }

    implicit val apiRequestExecutor: ApiRequestExecutor = ApiRequestExecutor

    run(arguments)
  }

  private[bcc] def run(arguments: ArgumentParser)(implicit trace: Trace, apiRequestExecutor: ApiRequestExecutor): Unit = {

    if (arguments.noArgs || arguments.contains(CmdLine.help)) {
      showHelp(arguments.params.filterNot(_ == CmdLine.help))
    } else {

      def hasArgument(arg: String): Boolean = {
        val result = arguments.contains(arg)
        if (result) trace(arg)
        result
      }

      implicit val system: ActorSystem = ActorSystem("SingleRequest")
      import system.dispatcher

      Try {

        val url = arguments(CmdLine.baseUrl).getOrElse(defaultBaseUrl)

        trace(s"baseurl:$url")

        val api = BccApi(url)

        if (hasArgument(CmdLine.netInfo)) {
          unwrap[BccApiCodec.NetworkInfo](api.networkInfo.executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.netClockInfo)) {
          val forceNtpCheck = arguments(CmdLine.forceNtpCheck).map(_.toBoolean)
          unwrap[BccApiCodec.NetworkClock](api.networkClock(forceNtpCheck).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.netParams)) {
          unwrap[BccApiCodec.NetworkParameters](api.networkParameters().executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.listWallets)) {
          unwrap[Seq[BccApiCodec.Wallet]](api.listWallets.executeBlocking, r => r.foreach(trace(_)))
        } else if (hasArgument(CmdLine.estimateFee)) {
          val walletId = arguments.get(CmdLine.walletId)
          val amount = arguments.get(CmdLine.amount).toLong
          val addr = arguments.get(CmdLine.address)
          val singlePayment = Payment(addr, QuantityUnit(amount, Units.entropic))
          val payments = Payments(Seq(singlePayment))
          unwrap[BccApiCodec.EstimateFeeResponse](api.estimateFee(walletId, payments, None).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.estimateFeeStakePool)) {
          val walletId = arguments.get(CmdLine.walletId)
          unwrap[BccApiCodec.EstimateFeeResponse](api.estimateFeeStakePool(walletId).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.getWallet)) {
          val walletId = arguments.get(CmdLine.walletId)
          unwrap[BccApiCodec.Wallet](api.getWallet(walletId).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.updatePassphrase)) {
          val walletId = arguments.get(CmdLine.walletId)
          val oldPassphrase = arguments.get(CmdLine.oldPassphrase)
          val newPassphrase = arguments.get(CmdLine.passphrase)
          unwrap[Unit](api.updatePassphrase(walletId, oldPassphrase, newPassphrase).executeBlocking, _ => trace("Unit result from update passphrase"))
        } else if (hasArgument(CmdLine.updateName)) {
          val walletId = arguments.get(CmdLine.walletId)
          val name = arguments.get(CmdLine.name)
          unwrap[BccApiCodec.Wallet](api.updateName(walletId, name).executeBlocking,trace(_))
        } else if (hasArgument(CmdLine.deleteWallet)) {
          val walletId = arguments.get(CmdLine.walletId)
          unwrap[Unit](api.deleteWallet(walletId).executeBlocking, _ => trace("Unit result from delete wallet"))
        } else if (hasArgument(CmdLine.listWalletAddresses)) {
          val walletId = arguments.get(CmdLine.walletId)
          val addressesState = Some(AddressFilter.withName(arguments.get(CmdLine.state)))
          unwrap[Seq[BccApiCodec.WalletAddressId]](api.listAddresses(walletId, addressesState).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.inspectWalletAddress)) {
          val address = arguments.get(CmdLine.address)
          unwrap[WalletAddress](api.inspectAddress(address).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.getTx)) {
          val walletId = arguments.get(CmdLine.walletId)
          val txId = arguments.get(CmdLine.txId)
          unwrap[BccApiCodec.CreateTransactionResponse](api.getTransaction(walletId, txId).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.deleteTx)) {
          val walletId = arguments.get(CmdLine.walletId)
          val txId = arguments.get(CmdLine.txId)
          unwrap[Unit](api.deleteTransaction(walletId, txId).executeBlocking, _ => trace("Unit result from delete transaction"))
        } else if (hasArgument(CmdLine.createTx)) {
          val walletId = arguments.get(CmdLine.walletId)
          val amount = arguments.get(CmdLine.amount).toLong
          val addr = arguments.get(CmdLine.address)
          val pass = arguments.get(CmdLine.passphrase)
          val metadata = toMetaMap(arguments(CmdLine.metadata))
          val singlePayment = Payment(addr, QuantityUnit(amount, Units.entropic))
          val payments = Payments(Seq(singlePayment))

          unwrap[BccApiCodec.CreateTransactionResponse](api.createTransaction(
            walletId,
            pass,
            payments,
            metadata,
            None
          ).executeBlocking, trace(_))

        } else if (hasArgument(CmdLine.fundTx)) {
          val walletId = arguments.get(CmdLine.walletId)
          val amount = arguments.get(CmdLine.amount).toLong
          val addr = arguments.get(CmdLine.address)
          val singlePayment = Payment(addr, QuantityUnit(amount, Units.entropic))
          val payments = Payments(Seq(singlePayment))

          unwrap[BccApiCodec.FundPaymentsResponse](api.fundPayments(
            walletId,
            payments
          ).executeBlocking, r => trace(r.toString))

        } else if (hasArgument(CmdLine.listWalletTransactions)) {
          val walletId = arguments.get(CmdLine.walletId)
          val startDate = arguments(CmdLine.start).map(strToZonedDateTime)
          val endDate = arguments(CmdLine.end).map(strToZonedDateTime)
          val orderOf = arguments(CmdLine.order).flatMap(s => Try(Order.withName(s)).toOption).getOrElse(Order.descendingOrder)
          val minWithdrawalTx = arguments(CmdLine.minWithdrawal).map(_.toInt)

          unwrap[Seq[BccApiCodec.CreateTransactionResponse]](api.listTransactions(
            walletId,
            startDate,
            endDate,
            orderOf,
            minWithdrawal = minWithdrawalTx
          ).executeBlocking, r => if (r.isEmpty) trace("No txs returned") else r.foreach(trace(_)))

        } else if (hasArgument(CmdLine.createWallet) || hasArgument(CmdLine.restoreWallet)) {
          val name = arguments.get(CmdLine.name)
          val passphrase = arguments.get(CmdLine.passphrase)
          val mnemonic = arguments.get(CmdLine.mnemonic)
          val mnemonicSecondaryOpt = arguments(CmdLine.mnemonicSecondary)
          val addressPoolGap = arguments(CmdLine.addressPoolGap).map(_.toInt)

          unwrap[BccApiCodec.Wallet](api.createRestoreWallet(
            name,
            passphrase,
            GenericMnemonicSentence(mnemonic),
            mnemonicSecondaryOpt.map(m => GenericMnemonicSecondaryFactor(m)),
            addressPoolGap
          ).executeBlocking, trace(_))

        } else if (hasArgument(CmdLine.createWalletWithKey) || hasArgument(CmdLine.restoreWalletWithKey)) {
          val name = arguments.get(CmdLine.name)
          val accountPublicKey = arguments.get(CmdLine.accountPublicKey)
          val addressPoolGap = arguments(CmdLine.addressPoolGap).map(_.toInt)

          unwrap[BccApiCodec.Wallet](api.createRestoreWalletWithKey(
            name,
            accountPublicKey,
            addressPoolGap
          ).executeBlocking, trace(_))

        } else if (hasArgument(CmdLine.getUTxOsStatistics)) {
          val walletId = arguments.get(CmdLine.walletId)
          unwrap[UTxOStatistics](api.getUTxOsStatistics(walletId).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.postExternalTransaction)) {
          val binary = arguments.get(CmdLine.binary)
          unwrap[PostExternalTransactionResponse](api.postExternalTransaction(binary).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.migrateSophieWallet)) {
          val walletId = arguments.get(CmdLine.walletId)
          val passphrase = arguments.get(CmdLine.passphrase)
          val addresses = arguments.get(CmdLine.addresses).split(",").toSeq
          unwrap[Seq[MigrationResponse]](api.migrateSophieWallet(walletId, passphrase, addresses).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.getSophieWalletMigrationInfo)) {
          val walletId = arguments.get(CmdLine.walletId)
          unwrap[MigrationCostResponse](api.getSophieWalletMigrationInfo(walletId).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.listStakePools)) {
          val stake = arguments.get(CmdLine.stake).toInt
          unwrap[Seq[StakePool]](api.listStakePools(stake).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.joinStakePool)) {
          val walletId = arguments.get(CmdLine.walletId)
          val stakePoolId = arguments.get(CmdLine.stakePoolId)
          val passphrase = arguments.get(CmdLine.passphrase)
          unwrap[MigrationResponse](api.joinStakePool(walletId, stakePoolId, passphrase).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.quitStakePool)) {
          val walletId = arguments.get(CmdLine.walletId)
          val passphrase = arguments.get(CmdLine.passphrase)
          unwrap[MigrationResponse](api.quitStakePool(walletId, passphrase).executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.stakePoolGetMaintenanceActions)) {
          unwrap[StakePoolMaintenanceActionsStatus](api.getMaintenanceActions().executeBlocking, trace(_))
        } else if (hasArgument(CmdLine.stakePoolPostMaintenanceActions)) {
          unwrap[Unit](api.postMaintenanceAction().executeBlocking, trace(_))
        } else {
          trace("No command recognised")
        }

      }.recover {
        case e => trace(e.toString)
      }
      trace.close()
      system.terminate()

    }
  }


  private def strToZonedDateTime(dtStr: String): ZonedDateTime = {
    ZonedDateTime.parse(dtStr)
  }

  private def showHelp(extraParams: List[String])(implicit trace: Trace): Unit = {
    val exampleBinary = "82839f8200d8185824825820d78b4cf8eb832c2207a9a2c787ec232d2fbf88ad432c05bfae9bff58d756d59800f"
    val exampleWalletId = "1234567890123456789012345678901234567890"
    val exampleTxd = "ABCDEF1234567890"
    val exampleAddress = "addr12345678901234567890123456789012345678901234567890123456789012345678901234567890"
    val exampleMetadata = "0:0123456789012345678901234567890123456789012345678901234567890123:2:TESTINGBCCAPI"
    val exampleMnemonic = "ability make always any pulse swallow marriage media dismiss degree edit spawn distance state dad"
    val exampleMnemonicSecondary = "ability make always any pulse swallow marriage media dismiss"

    def beautifyTrace(arguments: String, description: String, examples: List[String], apiDocOperation: String = ""): Unit = {
      val docsUrl = if (apiDocOperation.nonEmpty) s" [ https://The-Blockchain-Company.github.io/bcc-wallet/api/edge/#operation/$apiDocOperation ]\n" else ""
      val examplesStr = s" Examples:\n ${examples.map("$CMDLINE "+_).mkString("\n ")}"
      val argumentsLine = if (arguments.nonEmpty) s" Arguments: $arguments\n\n" else ""
      trace(s"\n $description\n$docsUrl\n$argumentsLine$examplesStr\n")
    }

    val cmdLineNetInfo = s"${CmdLine.netInfo}"
    val cmdLineNetClockInfo = s"${CmdLine.netClockInfo}"
    val cmdLineNetParams = s"${CmdLine.netParams}"
    val cmdLineListWallets = s"${CmdLine.listWallets}"
    val cmdLineEstimateFee = s"${CmdLine.estimateFee} ${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address>"
    val cmdLineEstimateFeeStakePool = s"${CmdLine.estimateFeeStakePool} ${CmdLine.walletId} <walletId>"
    val cmdLineGetWallet = s"${CmdLine.getWallet} ${CmdLine.walletId} <walletId>"
    val cmdLineUpdateName = s"${CmdLine.updateName} ${CmdLine.walletId} <walletId> ${CmdLine.name} <name>"
    val cmdLineUpdatePassphrase = s"${CmdLine.updatePassphrase} ${CmdLine.walletId} <walletId> ${CmdLine.oldPassphrase} <oldPassphrase> ${CmdLine.passphrase} <newPassphrase>"
    val cmdLineDeleteWallet = s"${CmdLine.deleteWallet} ${CmdLine.walletId} <walletId>"
    val cmdLineListWalletAddresses = s"${CmdLine.listWalletAddresses} ${CmdLine.walletId} <walletId> ${CmdLine.state} <state>"
    val cmdLineInspectWalletAddress = s"${CmdLine.inspectWalletAddress} ${CmdLine.address} <address>"
    val cmdLineGetTx = s"${CmdLine.getTx} ${CmdLine.walletId} <walletId> ${CmdLine.txId} <txId>"
    val cmdLineCreateTx = s"${CmdLine.createTx} ${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address> ${CmdLine.passphrase} <passphrase> [${CmdLine.metadata} <metadata>]"
    val cmdLineDeleteTx = s"${CmdLine.deleteTx} ${CmdLine.walletId} <walletId> ${CmdLine.txId} <txId>"
    val cmdLineFundTx = s"${CmdLine.fundTx} ${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address>"
    val cmdLineListWalletTransactions = s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} <walletId> [${CmdLine.start} <start_date>] [${CmdLine.end} <end_date>] [${CmdLine.order} <order>] [${CmdLine.minWithdrawal} <minWithdrawal>]"
    val cmdLineCreateWallet = s"${CmdLine.createWallet} ${CmdLine.name} <walletName> ${CmdLine.passphrase} <passphrase> ${CmdLine.mnemonic} <mnemonic> [${CmdLine.mnemonicSecondary} <mnemonicSecondary>] [${CmdLine.addressPoolGap} <mnemonicaddress_pool_gap>]"
    val cmdLineCreateWalletWithKey = s"${CmdLine.createWalletWithKey} ${CmdLine.name} <walletName> ${CmdLine.accountPublicKey} <accountPublicKey> [${CmdLine.addressPoolGap} <mnemonicaddress_pool_gap>]"
    val cmdLineRestoreWallet = s"${CmdLine.restoreWallet} ${CmdLine.name} <walletName> ${CmdLine.passphrase} <passphrase> ${CmdLine.mnemonic} <mnemonic> [${CmdLine.mnemonicSecondary} <mnemonicSecondary>] [${CmdLine.addressPoolGap} <mnemonicaddress_pool_gap>]"
    val cmdLineRestoreWalletWithKey = s"${CmdLine.restoreWalletWithKey} ${CmdLine.name} <walletName> ${CmdLine.accountPublicKey} <accountPublicKey> [${CmdLine.addressPoolGap} <mnemonicaddress_pool_gap>]"
    val cmdLineGetUTxOsStatistics = s"${CmdLine.getUTxOsStatistics} ${CmdLine.walletId} <walletId>"
    val cmdLinePostExternalTransaction = s"${CmdLine.postExternalTransaction} ${CmdLine.binary} <binary_string>"
    val cmdLineMigrateSophieWallet = s"${CmdLine.migrateSophieWallet} ${CmdLine.walletId} <walletId> ${CmdLine.passphrase} <passphrase> ${CmdLine.addresses} <addresses>"
    val cmdLineGetSophieWalletMigrationInfo = s"${CmdLine.getSophieWalletMigrationInfo} ${CmdLine.walletId} <walletId>"
    val cmdLineListStakePools = s"${CmdLine.listStakePools} ${CmdLine.stake} <stake>"
    val cmdLineJoinStakePool = s"${CmdLine.joinStakePool} ${CmdLine.walletId} <walletId> ${CmdLine.stakePoolId} <stakePoolId> ${CmdLine.passphrase} <passphrase>"
    val cmdLineQuitStakePool = s"${CmdLine.quitStakePool} ${CmdLine.walletId} <walletId> ${CmdLine.passphrase} <passphrase>"
    val cmdLineStakePoolGetMaintenanceActions = s"${CmdLine.stakePoolGetMaintenanceActions}"
    val cmdLineStakePoolPostMaintenanceActions = s"${CmdLine.stakePoolPostMaintenanceActions}"

    val cmdLineBaseUrl = s"${CmdLine.baseUrl} <url> <command>"
    val cmdLineTraceToFile = s"${CmdLine.traceToFile} <filename> <command>"
    val cmdLineNoConsole = s"${CmdLine.noConsole} <command>"

    if (extraParams.isEmpty) {
      trace("This super simple tool allows developers to access a bcc wallet backend from the command line\n")
      trace("Usage:\n")
      trace(" export CMDLINE='java -jar psg-bcc-wallet-api-assembly-<VER>.jar'")
      trace(" $CMDLINE <command> <arguments>\n")

      trace("Optional:\n")
      trace(" "+cmdLineBaseUrl)
      trace(" "+cmdLineTraceToFile)
      trace(" "+cmdLineNoConsole)

      trace("\nCommands:\n")
      trace(" "+cmdLineNetInfo)
      trace(" "+cmdLineNetClockInfo)
      trace(" "+cmdLineNetParams)
      trace(" "+cmdLineListWallets)
      trace(" "+cmdLineDeleteWallet)
      trace(" "+cmdLineGetWallet)
      trace(" "+cmdLineUpdateName)
      trace(" "+cmdLineCreateWallet)
      trace(" "+cmdLineCreateWalletWithKey)
      trace(" "+cmdLineRestoreWallet)
      trace(" "+cmdLineRestoreWalletWithKey)
      trace(" "+cmdLineEstimateFee)
      trace(" "+cmdLineEstimateFeeStakePool)
      trace(" "+cmdLineUpdatePassphrase)
      trace(" "+cmdLineListWalletAddresses)
      trace(" "+cmdLineInspectWalletAddress)
      trace(" "+cmdLineListWalletTransactions)
      trace(" "+cmdLineCreateTx)
      trace(" "+cmdLineDeleteTx)
      trace(" "+cmdLineFundTx)
      trace(" "+cmdLineGetTx)
      trace(" "+cmdLineGetUTxOsStatistics)
      trace(" "+cmdLinePostExternalTransaction+" ( experimental )")
      trace(" "+cmdLineMigrateSophieWallet)
      trace(" "+cmdLineGetSophieWalletMigrationInfo)
      trace(" "+cmdLineListStakePools)
      trace(" "+cmdLineJoinStakePool)
      trace(" "+cmdLineQuitStakePool)
      trace(" "+cmdLineStakePoolGetMaintenanceActions)
      trace(" "+cmdLineStakePoolPostMaintenanceActions)
    } else {
      extraParams.headOption.getOrElse("") match {
        case CmdLine.baseUrl =>
          beautifyTrace(
            arguments = "<url> <command>",
            description = s"define different api url ( default : ${BccApiMain.defaultBaseUrl} )",
            examples = List(
              s"${CmdLine.baseUrl} http://bcc-wallet-testnet.mydomain:8090/v2/ ${CmdLine.listWallets}"
            )
          )
        case CmdLine.traceToFile =>
          beautifyTrace(
            arguments = "<filename> <command>",
            description = s"write logs into a defined file ( default file name: ${BccApiMain.defaultTraceFile} )",
            examples = List(
              s"${CmdLine.traceToFile} wallets.log ${CmdLine.listWallets}"
            )
          )
        case CmdLine.noConsole =>
          beautifyTrace(
            arguments = "<command>",
            description = "run a command without any logging",
            examples = List(
              s"${CmdLine.noConsole} ${CmdLine.deleteWallet} ${CmdLine.walletId} $exampleWalletId"
            )
          )
        case CmdLine.netInfo =>
          beautifyTrace(
            arguments = "",
            description = "Show network information",
            apiDocOperation = "getNetworkInformation",
            examples = List(
              s"${CmdLine.netInfo}"
            )
          )
        case CmdLine.netClockInfo =>
          beautifyTrace(
            arguments = s"[${CmdLine.forceNtpCheck} <forceNtpCheck>]",
            description = "Show network clock information",
            apiDocOperation = "getNetworkClock",
            examples = List(
              s"${CmdLine.netClockInfo}",
              s"${CmdLine.netClockInfo} ${CmdLine.forceNtpCheck} true",
            )
          )
        case CmdLine.netParams =>
          beautifyTrace(
            arguments = "",
            description = "Returns the set of network parameters for the current epoch.",
            apiDocOperation = "getNetworkParameters",
            examples = List(
              s"${CmdLine.netParams}"
            )
          )
        case CmdLine.listWallets =>
          beautifyTrace(
            arguments = "",
            description = "Return a list of known wallets, ordered from oldest to newest",
            apiDocOperation = "listWallets",
            examples = List(
              s"${CmdLine.listWallets}"
            )
          )
        case CmdLine.deleteWallet =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId>",
            description = "Delete wallet by id",
            apiDocOperation = "deleteWallet",
            examples = List(
              s"${CmdLine.deleteWallet} ${CmdLine.walletId} $exampleWalletId"
            )
          )
        case CmdLine.getWallet =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId>",
            description = "Get wallet by id",
            apiDocOperation = "getWallet",
            examples = List(
              s"${CmdLine.getWallet} ${CmdLine.walletId} $exampleWalletId"
            )
          )
        case CmdLine.updateName =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.name} <name>",
            description = "Update wallet's name",
            apiDocOperation = "putWallet",
            examples = List(
              s"${CmdLine.updateName} ${CmdLine.walletId} $exampleWalletId ${CmdLine.name} new_name"
            )
          )
        case CmdLine.createWallet =>
          beautifyTrace(
            arguments = s"${CmdLine.name} <walletName> ${CmdLine.passphrase} <passphrase> ${CmdLine.mnemonic} <mnemonic> [${CmdLine.mnemonicSecondary} <mnemonicSecondary>] [${CmdLine.addressPoolGap} <address_pool_gap>]",
            description = "Create new wallet ( mnemonic can be generated on: https://iancoleman.io/bip39/ )",
            apiDocOperation = "postWallet",
            examples = List(
              s"${CmdLine.createWallet} ${CmdLine.name} new_wallet_1 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic'",
              s"${CmdLine.createWallet} ${CmdLine.name} new_wallet_1 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic' ${CmdLine.mnemonicSecondary} '$exampleMnemonicSecondary'",
              s"${CmdLine.createWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic' ${CmdLine.addressPoolGap} 10"
            )
          )

        case CmdLine.createWalletWithKey =>
          beautifyTrace(
            arguments = s"${CmdLine.name} <walletName> ${CmdLine.accountPublicKey} <accountPublicKey> [${CmdLine.addressPoolGap} <address_pool_gap>]",
            description = "Create new wallet ( mnemonic can be generated on: https://iancoleman.io/bip39/ )",
            apiDocOperation = "postWallet",
            examples = List(
              s"${CmdLine.createWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.accountPublicKey} accountkey",
              s"${CmdLine.createWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.accountPublicKey} accountkey ${CmdLine.addressPoolGap} 10",
            )
          )

        case CmdLine.restoreWallet =>
          beautifyTrace(
            arguments = s"${CmdLine.name} <walletName> ${CmdLine.passphrase} <passphrase> ${CmdLine.mnemonic} <mnemonic> [${CmdLine.mnemonicSecondary} <mnemonicSecondary>] [${CmdLine.addressPoolGap} <mnemonicaddress_pool_gap>]",
            description = "Restore wallet ( mnemonic can be generated on: https://iancoleman.io/bip39/ )",
            apiDocOperation = "postWallet",
            examples = List(
              s"${CmdLine.restoreWallet} ${CmdLine.name} new_wallet_1 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic'",
              s"${CmdLine.restoreWallet} ${CmdLine.name} new_wallet_1 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic' ${CmdLine.mnemonicSecondary} '$exampleMnemonicSecondary'",
              s"${CmdLine.restoreWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.passphrase} Password12345! ${CmdLine.mnemonic} '$exampleMnemonic' ${CmdLine.addressPoolGap} 10")
          )
        case CmdLine.restoreWalletWithKey =>
          beautifyTrace(
            arguments = s"${CmdLine.name} <walletName> ${CmdLine.accountPublicKey} <accountPublicKey> [${CmdLine.addressPoolGap} <address_pool_gap>]",
            description = "Restore wallet ( mnemonic can be generated on: https://iancoleman.io/bip39/ )",
            apiDocOperation = "postWallet",
            examples = List(
              s"${CmdLine.restoreWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.accountPublicKey} accountkey",
              s"${CmdLine.restoreWallet} ${CmdLine.name} new_wallet_2 ${CmdLine.accountPublicKey} accountkey ${CmdLine.addressPoolGap} 10"
            )
          )
        case CmdLine.estimateFee =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address>",
            description = "Estimate fee for the transaction",
            apiDocOperation = "postTransactionFee",
            examples = List(
              s"${CmdLine.estimateFee} ${CmdLine.walletId} $exampleWalletId ${CmdLine.amount} 20000 ${CmdLine.address} $exampleAddress"
            )
          )
        case CmdLine.estimateFeeStakePool =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId>",
            description = "Estimate fee for joining or leaving a stake pool",
            apiDocOperation = "getDelegationFee",
            examples = List(
              s"${CmdLine.estimateFeeStakePool} ${CmdLine.walletId} $exampleWalletId"
            )
          )
        case CmdLine.updatePassphrase =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.oldPassphrase} <oldPassphrase> ${CmdLine.passphrase} <newPassphrase>",
            description = "Update passphrase",
            apiDocOperation = "putWalletPassphrase",
            examples = List(
              s"${CmdLine.updatePassphrase} ${CmdLine.walletId} $exampleWalletId ${CmdLine.oldPassphrase} OldPassword12345! ${CmdLine.passphrase} NewPassword12345!"
            )
          )
        case CmdLine.listWalletAddresses =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.state} <state>",
            description = "Return a list of known addresses, ordered from newest to oldest, state: used, unused",
            apiDocOperation = "listAddresses",
            examples = List(
              s"${CmdLine.listWalletAddresses} ${CmdLine.walletId} $exampleWalletId ${CmdLine.state} ${AddressFilter.used}",
              s"${CmdLine.listWalletAddresses} ${CmdLine.walletId} $exampleWalletId ${CmdLine.state} ${AddressFilter.unUsed}"
            )
          )
        case CmdLine.inspectWalletAddress =>
          beautifyTrace(
            arguments = s"${CmdLine.address} <address>",
            description = "Give useful information about the structure of a given address.",
            apiDocOperation = "inspectAddress",
            examples = List(
              s"${CmdLine.inspectWalletAddress} ${CmdLine.address} $exampleAddress"
            )
          )
        case CmdLine.listWalletTransactions =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> [${CmdLine.start} <start_date>] [${CmdLine.end} <end_date>] [${CmdLine.order} <order>] [${CmdLine.minWithdrawal} <minWithdrawal>]",
            description = "Lists all incoming and outgoing wallet's transactions, dates in ISO_ZONED_DATE_TIME format, order: ascending, descending ( default )",
            apiDocOperation = "listTransactions",
            examples = List(
              s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} $exampleWalletId",
              s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} $exampleWalletId ${CmdLine.start} 2020-01-02T10:15:30+01:00",
              s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} $exampleWalletId ${CmdLine.start} 2020-01-02T10:15:30+01:00 ${CmdLine.end} 2020-09-30T12:00:00+01:00",
              s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} $exampleWalletId ${CmdLine.order} ${Order.ascendingOrder}",
              s"${CmdLine.listWalletTransactions} ${CmdLine.walletId} $exampleWalletId ${CmdLine.minWithdrawal} 1"
            )
          )
        case CmdLine.createTx =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address> ${CmdLine.passphrase} <passphrase> [${CmdLine.metadata} <metadata>]",
            description = "Create and send transaction from the wallet",
            apiDocOperation = "postTransaction",
            examples = List(
              s"${CmdLine.createTx} ${CmdLine.walletId} $exampleWalletId ${CmdLine.amount} 20000 ${CmdLine.address} $exampleAddress ${CmdLine.passphrase} Password12345!",
              s"${CmdLine.createTx} ${CmdLine.walletId} $exampleWalletId ${CmdLine.amount} 20000 ${CmdLine.address} $exampleAddress ${CmdLine.passphrase} Password12345! ${CmdLine.metadata} $exampleMetadata",
            )
          )
        case CmdLine.fundTx =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.amount} <amount> ${CmdLine.address} <address>",
            description = "Select coins to cover the given set of payments",
            apiDocOperation = "selectCoins",
            examples = List(
              s"${CmdLine.fundTx} ${CmdLine.walletId} $exampleWalletId ${CmdLine.amount} 20000 ${CmdLine.address} $exampleAddress"
            )
          )
        case CmdLine.getTx =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.txId} <txId>",
            description = "Get transaction by id",
            apiDocOperation = "getTransaction",
            examples = List(
              s"${CmdLine.getTx} ${CmdLine.walletId} $exampleWalletId ${CmdLine.txId} $exampleTxd"
            )
          )
        case CmdLine.deleteTx =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.txId} <txId>",
            description = "Delete pending transaction by id",
            apiDocOperation = "deleteTransaction",
            examples = List(
              s"${CmdLine.deleteTx} ${CmdLine.walletId} $exampleWalletId ${CmdLine.txId} $exampleTxd"
            )
          )
        case CmdLine.getUTxOsStatistics =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId>",
            description = "Return the UTxOs distribution across the whole wallet, in the form of a histogram",
            apiDocOperation = "getUTxOsStatistics",
            examples = List(
              s"${CmdLine.getUTxOsStatistics} ${CmdLine.walletId} $exampleWalletId"
            )
          )
        case CmdLine.postExternalTransaction =>
          beautifyTrace(
            arguments = s"${CmdLine.binary} <binary>",
            description = "Submits a transaction that was created and signed outside of bcc-wallet ( experimental )",
            apiDocOperation = "postExternalTransaction",
            examples = List(
              s"${CmdLine.postExternalTransaction} ${CmdLine.binary} $exampleBinary"
            )
          )
        case CmdLine.migrateSophieWallet =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.passphrase} <passphrase> ${CmdLine.addresses} <addresses>",
            description = "Submit one or more transactions which transfers all funds from a Sophie wallet to a set of addresses",
            apiDocOperation = "migrateSophieWallet",
            examples = List(
              s"${CmdLine.migrateSophieWallet} ${CmdLine.walletId} $exampleWalletId ${CmdLine.passphrase} Password12345! ${CmdLine.addresses} <addresses>"
            )
          )
        case CmdLine.getSophieWalletMigrationInfo =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId>",
            description = "Calculate the exact cost of sending all funds from particular Sophie wallet to a set of addresses",
            apiDocOperation = "getSophieWalletMigrationInfo",
            examples = List(
              s"${CmdLine.getSophieWalletMigrationInfo} ${CmdLine.walletId} $exampleWalletId",
            )
          )
        case CmdLine.listStakePools =>
          beautifyTrace(
            arguments = s"${CmdLine.stake} <stake>",
            description = "List all known stake pools ordered by descending non_myopic_member_rewards",
            apiDocOperation = "listStakePools",
            examples = List(
              s"${CmdLine.listStakePools} ${CmdLine.stake} 10000"
            )
          )
        case CmdLine.joinStakePool =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.stakePoolId} <stakePoolId> ${CmdLine.passphrase} <passphrase>",
            description = "Delegate all (current and future) addresses from the given wallet to the given stake pool",
            apiDocOperation = "joinStakePool",
            examples = List(
              s"${CmdLine.walletId} $exampleWalletId ${CmdLine.passphrase} Password123!"
            )
          )
        case CmdLine.quitStakePool =>
          beautifyTrace(
            arguments = s"${CmdLine.walletId} <walletId> ${CmdLine.passphrase} <passphrase>",
            description = "Stop delegating completely, the wallet's stake will become inactive",
            apiDocOperation = "quitStakePool",
            examples = List(
              s"${CmdLine.walletId} $exampleWalletId ${CmdLine.passphrase} Password123!"
            )
          )
        case CmdLine.stakePoolGetMaintenanceActions =>
          beautifyTrace(
            arguments = s"${CmdLine.stakePoolGetMaintenanceActions}",
            description = "View maintenance actions",
            apiDocOperation = "getMaintenanceActions",
            examples = List(
              s"${CmdLine.stakePoolGetMaintenanceActions}"
            )
          )
        case CmdLine.stakePoolPostMaintenanceActions =>
          beautifyTrace(
            arguments = s"${CmdLine.stakePoolPostMaintenanceActions}",
            description = "Trigger maintenance actions",
            apiDocOperation = "postMaintenanceAction",
            examples = List(
              s"${CmdLine.stakePoolPostMaintenanceActions}"
            )
          )
        case cmd => trace(s"$cmd help not supported")
      }
    }
  }

  def unwrap[T: ClassTag](apiResult: BccApiResponse[T], onSuccess: T => Unit)(implicit t: Trace): Unit =
    unwrapOpt(Try(apiResult)).foreach(onSuccess)

  def unwrapOpt[T: ClassTag](apiResult: Try[BccApiResponse[T]])(implicit trace: Trace): Option[T] = apiResult match {
    case Success(Left(ErrorMessage(message, code))) =>
      trace(s"API Error message $message, code $code")
      None
    case Success(Right(t: T)) => Some(t)
    case Failure(exception) =>
      println(exception)
      None
  }

  def fail[T](msg: String): T = throw new RuntimeException(msg)

}
