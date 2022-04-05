package iog.psg.bcc.experimental.cli

import iog.psg.bcc.experimental.cli.param._
import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

import java.io.File

case class BccCliCmdAddressBuild(protected val builder: ProcessBuilderHelper)
  extends CliCmd
    with TestnetMagic
    with CopyShim
    with OutFile {

  override type CONCRETECASECLASS = BccCliCmdAddressBuild
  val copier = this

  def paymentVerificationKey(verificationKey: String): BccCliCmdAddressBuild =
    BccCliCmdAddressBuild(builder.withParam("--payment-verification-key", verificationKey))

  def paymentVerificationKeyFile(verificationKeyFile: File): BccCliCmdAddressBuild =
    BccCliCmdAddressBuild(builder.withParam("--payment-verification-key-file", verificationKeyFile))

  def paymentScriptFile(paymentScriptFile: File): BccCliCmdAddressBuild =
    BccCliCmdAddressBuild(builder.withParam("--payment-script-file", paymentScriptFile))

  def run(): Int = exitValue()
}