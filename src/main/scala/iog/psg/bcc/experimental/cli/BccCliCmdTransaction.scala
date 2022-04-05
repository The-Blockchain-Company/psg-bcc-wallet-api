package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}


case class BccCliCmdTransaction(protected val builder: ProcessBuilderHelper) extends CliCmd {

  lazy val calculateMinFee: BccCliCmdTransactionMinFee = {
    BccCliCmdTransactionMinFee(builder.withCommand("calculate-min-fee"))
  }

  lazy val buildRaw: BccCliCmdTransactionBuildRaw = {
    BccCliCmdTransactionBuildRaw(builder.withCommand("build-raw"))
  }

  lazy val witness: BccCliCmdTransactionWitness = {
    BccCliCmdTransactionWitness(builder.withCommand("witness"))
  }

  lazy val assemble: BccCliCmdTransactionAssemble = {
    BccCliCmdTransactionAssemble(builder.withCommand("assemble"))
  }

  lazy val submit: BccCliCmdTransactionSubmit = {
    BccCliCmdTransactionSubmit(builder.withCommand("submit"))
  }
}