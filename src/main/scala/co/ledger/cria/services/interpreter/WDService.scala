package co.ledger.cria.services.interpreter

import cats.effect.IO
import co.ledger.cria.logging.ContextLogging
import co.ledger.cria.models.interpreter.{WDBlock, WDOperation, WDTransaction}
import doobie.Transactor
import doobie.implicits._

class WDService(
    db: Transactor[IO]
) extends ContextLogging {

  def saveWDOperation(op: WDOperation): IO[Int] =
    WDQueries
      .saveWDOperation(op)
      .transact(db)

  def saveTransaction(tx: WDTransaction): IO[Int] =
    (
      for {
        txStatement <- WDQueries.saveTransaction(tx)
        _           <- WDQueries.saveInputs(tx, tx.inputs)
        _           <- WDQueries.saveOutputs(tx.outputs, tx.uid, tx.hash)
      } yield txStatement
    )
      .transact(db)

  def saveBlocks(blocks: List[WDBlock]): IO[Int] =
    WDQueries
      .saveBlocks(blocks)
      .transact(db)

}
