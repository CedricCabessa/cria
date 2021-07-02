package co.ledger.cria.services.interpreter

import cats.effect.IO
import co.ledger.cria.logging.ContextLogging
import co.ledger.cria.models.Sort
import co.ledger.cria.models.account.AccountId
import co.ledger.cria.models.interpreter.OperationToSave
import doobie._
import doobie.implicits._

class OperationService(
    db: Transactor[IO]
) extends ContextLogging {

  def deleteUnconfirmedOperations(accountId: AccountId): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  def removeFromCursor(accountId: AccountId, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def getUncomputedOperations(accountId: AccountId, sort: Sort) =
    OperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort)
      .transact(db)

  def saveOperations(operations: List[OperationToSave]) =
    OperationQueries.saveOperations(operations).transact(db)
}
