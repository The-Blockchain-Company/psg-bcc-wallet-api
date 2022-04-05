package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.ProcessBuilderHelper

import java.io.File

object TemplateClient {

  def main(args: Array[String]): Unit = {

    val TESTNET_MAGIC = 1097911063
    val workingDirPath = "/home/alan/apps/bcc-cli/"

    def makeFileName(name: String): File = {
      new File(new File(workingDirPath), name)
    }

    val builderSudo = ProcessBuilderHelper()
      .withCommand("echo")
      .withParam(args.head)

    val builder = ProcessBuilderHelper()
      .withCommand("sudo")
      .withCommand("-S")
      .withCommand("BCC_NODE_SOCKET_PATH=/var/lib/docker/volumes/bcc-cli_node-ipc/_data/node.socket")
      .withCommand("./bcc-cli")


    val outFile: File = makeFileName("protocol4.json")

    val cli = BccCli(builder)
      .query
      .protocolParameters
      .testnetMagic(TESTNET_MAGIC)
      .outFile(outFile)

    val all = cli.run()

    all.foreach(println)

    if (outFile.exists()) {
      println("ok")
    }

    BccCli(builder)
      .address
      .keyGen
      .verificationKeyFile(makeFileName("payVerKey1"))
      .signingKeyFile(makeFileName("paySignKey1"))
      .normalKey
      .run()

    BccCli(builder)
      .address
      .keyGen
      .verificationKeyFile(makeFileName("payVerKey2"))
      .signingKeyFile(makeFileName("paySignKey2"))
      .normalKey
      .run()

    // ./bcc-cli address key-hash --payment-verification-key-file $DIR/payVerKey1 > $DIR/keyHash1
    val hash1 = BccCli(builder)
      .address
      .keyHash
      .paymentVerificationFile(makeFileName("payVerKey1"))
      .run()

    val hash2 = BccCli(builder)
      .address
      .keyHash
      .paymentVerificationFile(makeFileName("payVerKey2"))
      .run()

    println(s"hash1 $hash1, hash2 $hash2")

    def makeScript(keyHash1: String, keyHash2: String): String = {
      s"""{ "type": "all", "scripts": [ { "type": "sig", "keyHash": "${keyHash1}" }, { "type": "sig", "keyHash": "${keyHash2}" } ] }"""
    }

    import java.nio.file.Files
    import java.nio.file.Paths

    Files.write(makeFileName("allMultiSigScript").toPath, makeScript(hash1, hash2).getBytes)

    BccCli(builder)
      .address
      .build
      .paymentScriptFile(makeFileName("allMultiSigScript"))
      .testnetMagic(TESTNET_MAGIC)
      .outFile(makeFileName("script.addr"))
      .run()
  }
}
