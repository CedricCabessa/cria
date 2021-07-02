package co.ledger.cria.itutils

import cats.effect.IO
import co.ledger.cria.itutils.models.{
  GetOperationsResult,
  GetUtxosResult,
  OperationPaginationState,
  PaginationCursor,
  PaginationToken
}
import co.ledger.cria.itutils.queries.OperationTestQueries
import co.ledger.cria.models.account.AccountId
import co.ledger.cria.models.interpreter.{CurrentBalance, Operation, TransactionView}
import co.ledger.cria.models.{Sort, TxHash}
import co.ledger.cria.services.interpreter.OperationQueries.{OpWithoutDetails, OperationDetails}
import co.ledger.cria.services.interpreter.{BalanceQueries, OperationQueries}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Chunk, Pipe, Stream}

class TestUtils(db: Transactor[IO]) {

  private val numberOfOperationsToBuildByQuery = 5

  def getOperations(
      accountId: AccountId,
      limit: Int,
      sort: Sort,
      cursor: Option[PaginationToken[OperationPaginationState]]
  ): IO[GetOperationsResult] = {
    for {
      operations <-
        Stream
          .evalSeq(OperationTestQueries.fetchOperations(accountId, limit, sort, cursor))
          .groupAdjacentBy(_.op.hash) // many operations by hash (RECEIVED AND SENT)
          .chunkN(numberOfOperationsToBuildByQuery)
          .flatMap { ops =>
            val txHashes = ops.map { case (txHash, _) => txHash }.toNel

            val inputsAndOutputs = Stream
              .emits(txHashes.toList)
              .flatMap(txHashes =>
                OperationTestQueries
                  .fetchOperationDetails(accountId, sort, txHashes)
              )

            Stream
              .chunk(ops)
              .covary[ConnectionIO]
              .zip(inputsAndOutputs)
          }
          .transact(db)
          .through(makeOperation)
          .compile
          .toList

      total <- OperationTestQueries
        .countOperations(accountId)
        .transact(db)

      // Build previous pagination by taking the first operation state.
      previousPagination <- operations.headOption
        .map { o =>
          OperationTestQueries
            .hasPreviousPage(accountId, o.uid, sort)
            .transact(db)
            .map {
              case true =>
                Some(
                  PaginationToken(
                    OperationPaginationState(o.uid, o.blockHeight.getOrElse(0)),
                    isNext = false
                  )
                )
              case false => None
            }
        }
        .getOrElse(IO.pure(None))

      // Build next pagination by taking the last operation state
      nextPagination <- operations.lastOption
        .map { o =>
          OperationTestQueries
            .hasNextPage(accountId, o.uid, sort)
            .transact(db)
            .map {
              case true =>
                Some(
                  PaginationToken(
                    OperationPaginationState(o.uid, o.blockHeight.getOrElse(0)),
                    isNext = true
                  )
                )
              case false => None
            }
        }
        .getOrElse(IO.pure(None))
    } yield {
      // Build the cursor pagination only if we have a previous or next pagination.
      val cursorPagination =
        Option
          .when(previousPagination.isDefined || nextPagination.isDefined) {
            PaginationCursor(
              previous = previousPagination.map(_.toBase64),
              next = nextPagination.map(_.toBase64)
            )
          }

      GetOperationsResult(operations, total, cursorPagination)
    }
  }

  private lazy val makeOperation: Pipe[
    IO,
    (
        (TxHash, Chunk[OpWithoutDetails]),
        OperationQueries.OperationDetails
    ),
    Operation
  ] =
    _.flatMap {
      case (
            (txHash, sentAndReceivedOperations),
            inputsWithOutputsByTxHash
          ) =>
        Stream
          .chunk(sentAndReceivedOperations)
          .takeWhile(_ => txHash == inputsWithOutputsByTxHash.txHash)
          .map { op =>
            operation(op, inputsWithOutputsByTxHash)
          }
    }

  private def operation(
      emptyOperation: OpWithoutDetails,
      inputsWithOutputsByTxHash: OperationDetails
  ) =
    Operation(
      uid = emptyOperation.op.uid,
      accountId = emptyOperation.op.accountId,
      hash = emptyOperation.op.hash.hex,
      transaction = TransactionView(
        id = emptyOperation.tx.id,
        hash = emptyOperation.tx.hash.hex,
        receivedAt = emptyOperation.tx.receivedAt,
        lockTime = emptyOperation.tx.lockTime,
        fees = emptyOperation.tx.fees,
        inputs = inputsWithOutputsByTxHash.inputs,
        outputs = inputsWithOutputsByTxHash.outputs,
        block = emptyOperation.tx.block,
        confirmations = emptyOperation.tx.confirmations
      ),
      operationType = emptyOperation.op.operationType,
      amount = emptyOperation.op.amount,
      fees = emptyOperation.op.fees,
      time = emptyOperation.op.time,
      blockHeight = emptyOperation.op.blockHeight
    )

  def getUtxos(
      accountId: AccountId,
      limit: Int,
      offset: Int,
      sort: Sort
  ): IO[GetUtxosResult] =
    for {
      utxos <- OperationTestQueries
        .fetchConfirmedUTXOs(accountId, sort, Some(limit + 1), Some(offset))
        .transact(db)
        .compile
        .toList

      total <- OperationTestQueries.countUTXOs(accountId).transact(db)

    } yield {
      // We get 1 more than necessary to know if there's more, then we return the correct number
      GetUtxosResult(utxos.slice(0, limit), total, truncated = utxos.size > limit)
    }

  def getBalance(accountId: AccountId): IO[CurrentBalance] =
    (for {
      blockchainBalance <- BalanceQueries.getBlockchainBalance(accountId)
      mempoolBalance    <- BalanceQueries.getUnconfirmedBalance(accountId)
    } yield {
      CurrentBalance(
        blockchainBalance.balance,
        blockchainBalance.utxos,
        blockchainBalance.received,
        blockchainBalance.sent,
        mempoolBalance
      )
    }).transact(db)
}
