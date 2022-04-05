package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param._
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdAddressBuildScript(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with TestnetMagic
    with CopyShim
    with OutFile {

  override type CONCRETECASECLASS = BccCliCmdAddressBuildScript
  val copier = this

  def withPaymentScriptFile(file: File): BccCliCmdAddressBuildScript = {
    copy(builder.withParam("--payment-script-file", file))
  }

  def run(): Int = exitValue()
  def string(): String = stringValue()
}