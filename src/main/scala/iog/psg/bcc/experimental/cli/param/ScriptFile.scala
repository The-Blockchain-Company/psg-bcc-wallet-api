package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

import java.io.File

trait ScriptFile {
  self: CliCmd with CopyShim =>

  def scriptFile(scriptFile: File): CONCRETECASECLASS =
    copier.copy(builder.withParam("--script-file", scriptFile))

}
