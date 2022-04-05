package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdAddressKeyHashString(protected val builder: ProcessBuilderHelper) extends CliCmd {
  def run(): String = stringValue()
}
