package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}
import iog.psg.bcc.experimental.cli.param._

case class BccCliCmdTransactionAssemble(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with TxBodyFile
    with OutFile
    with WitnessFile
    with CopyShim {

  override type CONCRETECASECLASS = BccCliCmdTransactionAssemble
  val copier = this

  def run(): Int = exitValue()
}
