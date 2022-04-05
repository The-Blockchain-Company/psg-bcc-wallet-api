package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait TxFile {
  self: CliCmd with CopyShim =>

  def txFile(txFile: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--tx-file", txFile))
}
