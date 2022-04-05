package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}
import iog.psg.bcc.experimental.cli.param._

import java.io.File

case class BccCliCmdQueryUtxo(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with SophieMode
    with CopyShim
    with TestnetMagic {

  def address(address:String): BccCliCmdQueryUtxo =
    copy(builder = builder.withParam("--address", address))

  def outFile(outFile: File): BccCliCmdQueryUtxo = {
    copy(builder.withParam("--out-file", outFile))
  }

  def run(): Int = exitValue()

  override type CONCRETECASECLASS = BccCliCmdQueryUtxo
  override protected def copier: BccCliCmdQueryUtxo = this
}