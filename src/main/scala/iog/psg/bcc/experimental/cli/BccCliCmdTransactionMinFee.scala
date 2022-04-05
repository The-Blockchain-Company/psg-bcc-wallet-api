package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param.{TestnetMagic, TxBodyFile}
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdTransactionMinFee(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with TxBodyFile
    with CopyShim
    with TestnetMagic {

  def protocolParamsFile(protocolParams: File): BccCliCmdTransactionMinFee =
    copy(builder.withParam("--protocol-params-file", protocolParams))

  def txInCount(in: Int): BccCliCmdTransactionMinFee =
    copy(builder.withParam("--tx-in-count", in.toString))

  def txOutCount(out: Int): BccCliCmdTransactionMinFee =
    copy(builder.withParam("--tx-out-count", out.toString))

  def witnessCount(witnessCount: Int):BccCliCmdTransactionMinFee =
    copy(builder.withParam("--witness-count", witnessCount.toString))

  def run(): String = stringValue()

  override type CONCRETECASECLASS = BccCliCmdTransactionMinFee
  override protected def copier = this
}
