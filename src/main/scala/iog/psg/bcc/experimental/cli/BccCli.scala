package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCli(builder: ProcessBuilderHelper) extends CliCmd {

  lazy val key: BccCliCmdKey = {
    BccCliCmdKey(builder.withCommand("key"))
  }

  lazy val address: BccCliCmdAddress = {
    BccCliCmdAddress(builder.withCommand("address"))
  }

  lazy val query: BccCliCmdQuery = {
    BccCliCmdQuery(builder.withCommand("query"))
  }

  lazy val transaction: BccCliCmdTransaction = {
    BccCliCmdTransaction(builder.withCommand("transaction"))
  }

  def withBccNodeSocketPath(path: String): BccCli = {
    copy(builder.withEnv("BCC_NODE_SOCKET_PATH", path))
  }

  def withSudo: BccCli = {
    copy(builder.withSudo)
  }
}

object BccCli {
  private val default: BccCli = BccCli("./bcc-cli")

  def apply(): BccCli = default

  def apply(pathToBccCli: String): BccCli = {
    BccCli(
      ProcessBuilderHelper()
        .withCommand(pathToBccCli)
    )
  }
}
