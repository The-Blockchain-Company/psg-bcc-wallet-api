package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait SigningKeyFile {
  self: CliCmd with CopyShim =>

  def signingKeyFile(scriptFile: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--signing-key-file", scriptFile))
}
