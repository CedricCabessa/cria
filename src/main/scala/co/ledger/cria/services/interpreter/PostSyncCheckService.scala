package co.ledger.cria.services.interpreter

import cats.effect.IO
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.account.AccountId
import co.ledger.cria.models.interpreter.CurrentBalance
import doobie._
import doobie.implicits._

class PostSyncCheckService(db: Transactor[IO]) extends ContextLogging {

  def check(accountId: AccountId): IO[Unit] = {
    implicit val lc: CriaLogContext = CriaLogContext().withAccountId(accountId)

    for {
      currentBalance <- getCurrentBalance(accountId)
      _              <- log.info(s"Balance is ${currentBalance}")
      _              <- checkBalance(currentBalance)
    } yield ()
  }

  private def getCurrentBalance(accountId: AccountId): IO[CurrentBalance] =
    for {
      blockchainBalance  <- BalanceQueries.getBlockchainBalance(accountId).transact(db)
      unconfirmedBalance <- BalanceQueries.getUnconfirmedBalance(accountId).transact(db)
    } yield CurrentBalance(
      balance = blockchainBalance.balance,
      utxos = blockchainBalance.utxos,
      received = blockchainBalance.received,
      sent = blockchainBalance.sent,
      unconfirmedBalance = unconfirmedBalance
    )

  private def checkBalance(balance: CurrentBalance): IO[Unit] = {
    val balanceFromFlow = balance.received - balance.sent
    IO.raiseUnless(balance.received - balance.sent == balance.balance)(
      new RuntimeException(
        s"Balance invariant is not respected. received - sent = ${balanceFromFlow}. balance = ${balance.balance}"
      )
    )
  }
}
