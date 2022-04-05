package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdAddressKeyGenNormalKey(protected val builder: ProcessBuilderHelper) extends CliCmd {
  def run(): Int = exitValue()
}