package iog.psg.bcc

import iog.psg.bcc.util.Configure

import scala.util.Try

final case class WalletConfig(
  id: String,
  name: String,
  passphrase: Option[String],
  mnemonic: Option[String],
  mnemonicSecondary: Option[String],
  amount: Option[String],
  metadata: Option[String],
  publicKey: Option[String]
)

object TestWalletsConfig extends Configure {

  lazy val baseUrl = config.getString("bcc.wallet.baseUrl")
  lazy val walletsMap = (1 to 4).map { num =>
    num -> loadWallet(num)
  }.toMap

  private def loadWallet(num: Int) = {
    val id = config.getString(s"bcc.wallet$num.id")
    val name = config.getString(s"bcc.wallet$num.name")

    val mnemonic = Try(config.getString(s"bcc.wallet$num.mnemonic")).toOption
    val mnemonicSecondary = Try(config.getString(s"bcc.wallet$num.mnemonicsecondary")).toOption
    val passphrase = Try(config.getString(s"bcc.wallet$num.passphrase")).toOption
    val amount = Try(config.getString(s"bcc.wallet$num.amount")).toOption
    val metadata = Try(config.getString(s"bcc.wallet$num.metadata")).toOption
    val publicKey = Try(config.getString(s"bcc.wallet$num.publickey")).toOption

    WalletConfig(id, name, passphrase, mnemonic, mnemonicSecondary, amount, metadata, publicKey)
  }
}
