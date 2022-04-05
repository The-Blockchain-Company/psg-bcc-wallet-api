package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

trait SophieMode {
  self: CliCmd with CopyShim =>

  lazy val sophieMode: CONCRETECASECLASS =
    copier.copy(builder.withParam("--sophie-mode"))
}
