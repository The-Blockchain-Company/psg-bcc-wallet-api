package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param.{JenEra, OutFile, SophieMode, TestnetMagic}
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdQueryProtocol(builder: ProcessBuilderHelper)
  extends CliCmd
    with TestnetMagic
    with SophieMode
    with OutFile
    with CopyShim {

  override type CONCRETECASECLASS = BccCliCmdQueryProtocol
  val copier = this

  def string(): String = stringValue()

  def run(): Seq[String] = {
    allValues()
  }
}
