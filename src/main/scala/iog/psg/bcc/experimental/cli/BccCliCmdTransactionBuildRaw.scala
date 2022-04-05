package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param.OutFile
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdTransactionBuildRaw(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with OutFile
    with CopyShim {

  def ttl(value: Long): BccCliCmdTransactionBuildRaw =
    copy(builder.withParam("--ttl", value.toString))

  def fee(value: Long): BccCliCmdTransactionBuildRaw =
    copy(builder.withParam("--fee", value.toString))

  def txIn(value: String): BccCliCmdTransactionBuildRaw =
    copy(builder.withParam("--tx-in", value))

  def txOut(value: String): BccCliCmdTransactionBuildRaw =
    copy(builder.withParam("--tx-out", value))

  def txinScriptFile(file: File): BccCliCmdTransactionBuildRaw =
    copy(builder.withParam("--txin-script-file", file))

  def run(): Int = exitValue()

  override type CONCRETECASECLASS = BccCliCmdTransactionBuildRaw
  override protected def copier = this
}