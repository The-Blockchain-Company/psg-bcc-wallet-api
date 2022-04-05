package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

trait JenEra {
  self: CliCmd with CopyShim =>

  lazy val jenEra: CONCRETECASECLASS =
    copier.copy(builder.withParam("--jen-era"))
}
