package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdAddressKeyHash(protected val builder: ProcessBuilderHelper) extends CliCmd {
  def paymentVerificationString(bech32EncodedKey: String): BccCliCmdAddressKeyHashString =
    BccCliCmdAddressKeyHashString(builder.withParam("--payment-verification-key", "'" + bech32EncodedKey + "'"))

  def paymentVerificationFile(pathToBech32EncodedKey: File): BccCliCmdAddressKeyHashFile =
    BccCliCmdAddressKeyHashFile(builder.withParam("--payment-verification-key-file", pathToBech32EncodedKey))
}