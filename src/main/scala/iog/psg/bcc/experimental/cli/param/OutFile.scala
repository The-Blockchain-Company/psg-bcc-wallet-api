package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait OutFile {
  self: CliCmd with CopyShim =>

  def outFile(txBody: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--out-file", txBody))
}
