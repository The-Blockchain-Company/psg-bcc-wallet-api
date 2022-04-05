package iog.psg.bcc.experimental.cli

import iog.psg.bcc.util.ProcessBuilderHelper

object SSH {

  def executeRemotely[T: ProcessResult](
    identityFile: String,
    host: String,
    command: String
  ): T = {
    import scala.util.chaining._

    ProcessBuilderHelper()
      .withCommand("ssh")
      .withParam("-i", identityFile)
      .withParam(host)
      .withParam(command)
      .processBuilder
      .pipe(ProcessResult[T].apply)
  }
}
