package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdQuery(protected val builder: ProcessBuilderHelper) extends CliCmd{

  /*
  protocol-parameters | tip | stake-distribution |
                         stake-address-info | utxo | ledger-state |
                         protocol-state | stake-snapshot | pool-params
   */
  lazy val protocolParameters: BccCliCmdQueryProtocol = {
    BccCliCmdQueryProtocol(builder.withCommand("protocol-parameters"))
  }

  lazy val utxo: BccCliCmdQueryUtxo = {
    BccCliCmdQueryUtxo(builder.withCommand("utxo"))
  }
}