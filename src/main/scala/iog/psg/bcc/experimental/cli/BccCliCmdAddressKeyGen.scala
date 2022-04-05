package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdAddressKeyGen(protected val builder: ProcessBuilderHelper) extends CliCmd {
  lazy val normalKey: BccCliCmdAddressKeyGenNormalKey =
    BccCliCmdAddressKeyGenNormalKey(builder.withParam("--normal-key"))

  def verificationKeyFile(verificationKeyFile: File): BccCliCmdAddressKeyGen = {
    BccCliCmdAddressKeyGen(
      builder.withParam("--verification-key-file", verificationKeyFile)
    )
  }

  def signingKeyFile(signingKeyFile: File): BccCliCmdAddressKeyGen = {
    BccCliCmdAddressKeyGen(
      builder.withParam("--signing-key-file", signingKeyFile)
    )
  }
}