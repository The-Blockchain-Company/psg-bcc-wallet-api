package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait TxBodyFile {
  self: CliCmd with CopyShim =>

  def txBodyFile(txBody: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--tx-body-file", txBody))
}
