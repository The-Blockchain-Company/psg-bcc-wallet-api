package iog.psg.bcc.experimental.cli.param

import iog.psg.bcc.experimental.cli.CopyShim
import iog.psg.bcc.util.CliCmd

trait TestnetMagic {
  self: CliCmd with CopyShim =>

  lazy val mainnet: CONCRETECASECLASS = {
    copier.copy(builder.withParam("--mainnet"))
  }

  def testnetMagic(magic: Long): CONCRETECASECLASS = {
    copier.copy(builder.withParam("--testnet-magic", magic.toString))
  }

  def testnetMagic: CONCRETECASECLASS = testnetMagic(1097911063)
}
