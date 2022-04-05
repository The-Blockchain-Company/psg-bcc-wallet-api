package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param.{TestnetMagic, TxFile}
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdTransactionSubmit(protected val builder: ProcessBuilderHelper)
   extends CliCmd
    with TestnetMagic
    with CopyShim
    with TxFile {


  type CONCRETECASECLASS = BccCliCmdTransactionSubmit
  protected def copier = this

  def run() = exitValue()

}