package co.ledger.cria.services.interpreter

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.{Sort, TxHash}
import co.ledger.cria.models.interpreter.{AccountTxView, BlockView, TransactionView}
import co.ledger.cria.models.account.AccountId
import doobie.Transactor
import doobie.implicits._
import fs2._

class TransactionService(db: Transactor[IO], maxConcurrent: Int) extends ContextLogging {

  def saveTransactions(implicit
      cs: ContextShift[IO],
      lc: CriaLogContext
  ): Pipe[IO, AccountTxView, Int] =
    _.chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunk =>
        Stream
          .chunk(chunk)
          .evalMap(a => TransactionQueries.saveTransaction(a.accountId, a.tx))
          .transact(db)
          .compile
          .foldMonoid
          .flatMap { nbSaved =>
            log.info(s"$nbSaved new transactions saved (from chunk size: ${chunk.size})") *>
              IO.pure(nbSaved)
          }
      }

  def removeFromCursor(accountId: AccountId, blockHeight: Long): IO[Int] =
    TransactionQueries
      .removeFromCursor(accountId, blockHeight)
      .flatMap(_ =>
        TransactionQueries
          .deleteUnconfirmedTransactions(accountId)
      )
      .transact(db)

  def getLastBlocks(accountId: AccountId): Stream[IO, BlockView] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

  def deleteUnconfirmedTransaction(accountId: AccountId, hash: String): IO[String] =
    TransactionQueries
      .deleteUnconfirmedTransaction(accountId, hash)
      .transact(db)

  def fetchTransactions(
      accountId: AccountId,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): Stream[IO, TransactionView] = {
    val txStream = TransactionQueries.fetchTransaction(accountId, sort, hashes).transact(db)
    val txDetailsStream =
      TransactionQueries.fetchTransactionDetails(accountId, sort, hashes).transact(db)

    txStream
      .zip(txDetailsStream)
      .collect {
        case (tx, details) if tx.hash == details.txHash.hex =>
          tx.copy(inputs = details.inputs, outputs = details.outputs)
      }

  }

}
