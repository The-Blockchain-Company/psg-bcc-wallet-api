package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}
import iog.psg.bcc.experimental.cli.param._

case class BccCliCmdTransactionWitness(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with CopyShim
    with TxBodyFile
    with OutFile
    with TestnetMagic
    with ScriptFile
    with SigningKeyFile {

  override type CONCRETECASECLASS = BccCliCmdTransactionWitness
  val copier = this

  def run(): Int = exitValue()
}