package iog.psg.bcc.experimental

import iog.psg.bcc.util.ProcessBuilderHelper

package object cli {

  trait CopyShim {
    type CONCRETECASECLASS
    protected def copier: CanCopy[CONCRETECASECLASS]
  }

  type CanCopy[F] = { def copy(c: ProcessBuilderHelper): F }
}
