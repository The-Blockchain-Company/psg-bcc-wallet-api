package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait WitnessFile {
  self: CliCmd with CopyShim =>

  def witnessFile(txBody: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--witness-file", txBody))
}
