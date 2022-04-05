package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.{CliCmd, ProcessBuilderHelper}

case class BccCliCmdAddress(protected val builder: ProcessBuilderHelper) extends CliCmd {

  lazy val keyHash: BccCliCmdAddressKeyHash =
    BccCliCmdAddressKeyHash(builder.withParam("key-hash"))

  lazy val keyGen: BccCliCmdAddressKeyGen =
    BccCliCmdAddressKeyGen(builder.withParam("key-gen"))

  lazy val buildScript: BccCliCmdAddressBuildScript =
    BccCliCmdAddressBuildScript(builder.withParam("build"))

  lazy val build: BccCliCmdAddressBuild =
    BccCliCmdAddressBuild(builder.withParam("build"))
}