package co.ledger.cria.services.interpreter

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.models.interpreter.{AccountAddress, Action, BlockView, TransactionView}
import co.ledger.cria.clients.http.ExplorerClient
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.{Sort, TxHash}
import co.ledger.cria.models.account.{Account, AccountId, Coin, WalletUid}
import co.ledger.cria.models.interpreter._
import co.ledger.cria.utils.IOUtils
import co.ledger.lama.bitcoin.interpreter.services.WDService
import doobie.Transactor
import fs2._

trait Interpreter {
  def saveTransactions(accountId: AccountId)(implicit
      lc: CriaLogContext
  ): Pipe[IO, TransactionView, Unit]

  def removeDataFromCursor(
      accountId: AccountId,
      blockHeightCursor: Option[Long],
      followUpId: SyncId
  )(implicit lc: CriaLogContext): IO[Int]

  def getLastBlocks(accountId: AccountId)(implicit lc: CriaLogContext): IO[List[BlockView]]

  def compute(account: Account, coin: Coin, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int]

}

class InterpreterImpl(
    explorer: Coin => ExplorerClient,
    db: Transactor[IO],
    maxConcurrent: Int,
    batchConcurrency: Db.BatchConcurrency
)(implicit cs: ContextShift[IO], t: Timer[IO])
    extends Interpreter
    with ContextLogging {

  val transactionService   = new TransactionService(db, maxConcurrent)
  val operationService     = new OperationService(db)
  val flaggingService      = new FlaggingService(db)
  val wdService            = new WDService(db)
  val postSyncCheckService = new PostSyncCheckService(db)

  def saveTransactions(
      accountId: AccountId
  )(implicit lc: CriaLogContext): Pipe[IO, TransactionView, Unit] = { transactions =>
    transactions
      .map(tx => AccountTxView(accountId, tx))
      .through(transactionService.saveTransactions)
      .void
  }

  def getLastBlocks(
      accountId: AccountId
  )(implicit lc: CriaLogContext): IO[List[BlockView]] = {
    log.info(s"Getting last known blocks") *>
      transactionService
        .getLastBlocks(accountId)
        .compile
        .toList
  }

  def removeDataFromCursor(
      accountId: AccountId,
      blockHeight: Option[Long],
      followUpId: SyncId
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _     <- log.info(s"""Deleting data with parameters:
                      - blockHeight: $blockHeight""")
      txRes <- transactionService.removeFromCursor(accountId, blockHeight.getOrElse(0L))
      _     <- log.info(s"Deleted $txRes operations")
    } yield txRes
  }

  def compute(account: Account, coin: Coin, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _ <- log.info(s"Flagging belonging inputs and outputs")
      _ <- flaggingService.flagInputsAndOutputs(account.id, addresses)
      _ <- operationService.deleteUnconfirmedOperations(account.id)

      _ <- log.info(s"Computing operations")

      nbSavedOps <- IOUtils.withTimer("Computing finished")(
        computeOperations(account.id, account.coin, walletUid).compile.foldMonoid
      )

      _ <- log.info(s"$nbSavedOps operations saved")

      _ <- postSyncCheckService.check(account.id)
    } yield nbSavedOps
  }

  private def computeOperations(accountId: AccountId, coin: Coin, walletUid: WalletUid)(implicit
      lc: CriaLogContext
  ): Stream[IO, Int] =
    getUncomputedTxs(accountId, coin, walletUid)(200)
      .evalMap(
        _.traverse(getAction(coin, _))
      )
      .evalTap(deleteRejectedTransaction)
      .evalTap(saveWDBlocks)
      .evalTap(saveWDTransactions)
      .evalMap(saveWDOperations)
      .foldMonoid

  private def getAction(
      coin: Coin,
      tx: WDTxToSave
  )(implicit lc: CriaLogContext): IO[Action] =
    tx.block match {
      case Some(_) => IO.pure(Save(tx))
      case None =>
        explorer(coin).getTransaction(tx.tx.hash).map {
          case Some(_) => Save(tx)
          case None    => Delete(tx)
        }
    }

  private def getUncomputedTxs(accountId: AccountId, coin: Coin, walletUid: WalletUid)(
      chunkSize: Int
  )(implicit
      lc: CriaLogContext
  ): Stream[IO, List[WDTxToSave]] = {
    val sort = Sort.Ascending
    operationService
      .getUncomputedOperations(accountId, sort)
      .chunkN(chunkSize)
      .evalMap(chunk => getWDTxToSave(accountId, coin, walletUid, sort, chunk.toList))
  }

  private def getWDTxToSave(
      accountId: AccountId,
      coin: Coin,
      walletUid: WalletUid,
      sort: Sort,
      uncomputedTransactions: List[TransactionAmounts]
  )(implicit lc: CriaLogContext): IO[List[WDTxToSave]] = {
    for {

      txToSaveMap: Map[String, TransactionView] <- NonEmptyList
        .fromList(uncomputedTransactions.map(_.hash))
        .map(hashNel =>
          transactionService
            .fetchTransactions(
              accountId,
              sort,
              hashNel.map(TxHash(_))
            )
            .map(tx => (tx.hash, tx))
            .compile
            .to(Map)
        )
        .getOrElse(IO.pure(Map.empty))

      operationMap = uncomputedTransactions.map { opToSave =>
        val txView = txToSaveMap(opToSave.hash)
        val block  = txView.block.map(WDBlock.fromBlock(_, coin))
        val wdTx   = WDTransaction.fromTransactionView(accountId, txView, block, coin)
        val ops =
          opToSave.computeOperations.map(
            WDOperation.fromOperation(_, coin, wdTx, walletUid.toString)
          )
        WDTxToSave(block, wdTx, ops)
      }

    } yield operationMap
  }

  private def saveWDBlocks(actions: List[Action]): IO[Int] =
    wdService.saveBlocks(actions.collect { case Save(a) =>
      a.block match {
        case Some(block) => block
      }
    })

  private def saveWDTransactions(actions: List[Action]): IO[Int] =
    actions
      .collect { case Save(a) =>
        wdService.saveTransaction(a.tx)
      }
      .sequence
      .map(_.sum)

  private def saveWDOperations(actions: List[Action]): IO[Int] =
    actions
      .flatMap {
        case Save(a) =>
          a.ops.map(wdService.saveWDOperation)
        case _ => Nil
      }
      .sequence
      .map(_.sum)

  private def deleteRejectedTransaction(actions: List[Action])(implicit
      lc: CriaLogContext
  ): IO[Int] =
    actions
      .collect { case Delete(tx) =>
        // TODO: remove from WD instead
        log.info(s"Deleting unconfirmed transaction from db : ${tx.tx.hash} (not in explorer)") *>
          transactionService.deleteUnconfirmedTransaction(tx.tx.hash) *>
          IO.pure(1)
      }
      .sequence
      .map(_.sum)

}
