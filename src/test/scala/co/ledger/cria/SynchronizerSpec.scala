package co.ledger.cria

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.domain.mocks.ExplorerClientMock
import co.ledger.cria.domain.Synchronizer
import co.ledger.cria.domain.models.SynchronizationParameters
import co.ledger.cria.domain.models.interpreter.{
  BlockView,
  Coin,
  CoinFamily,
  SyncId,
  TransactionView
}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import co.ledger.cria.domain.models.account.Account
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.domain.services.{CursorStateService, ExplorerClient}
import co.ledger.cria.domain.services.interpreter.{Interpreter, InterpreterClientMock}
import co.ledger.cria.utils.IOAssertion

import scala.concurrent.ExecutionContext

class SynchronizerSpec extends AnyFlatSpec with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val keychainId = KeychainId(UUID.randomUUID())

  val accountIdentifier: Account =
    Account(
      keychainId,
      CoinFamily.Bitcoin,
      Coin.Btc
    )
  val accountAddresses = LazyList.from(1).map(_.toString)
  val blockchain = LazyList
    .from(0)
    .map { i =>
      val block   = BlockView((i + 1000).toString, i, Instant.now())
      val address = i.toString
      block -> List(address -> List(TransactionFixture.confirmed.receive(address, inBlock = block)))
    }
    .take(5)
    .toList

  val mempool = LazyList
    .from(100)
    .map { i =>
      val address = i.toString
      address -> List(TransactionFixture.receive(address))
    }
    .take(1)
    .toList

  val defaultExplorer = new ExplorerClientMock(
    blockchain.flatMap(_._2).toMap
  )
  val alreadyValidBlockCursorService: CursorStateService[IO] = (_, b, _) => IO.pure(b)

  def worker(
      interpreter: Interpreter = new InterpreterClientMock,
      explorer: ExplorerClient = defaultExplorer
  ) = new Synchronizer(
    KeychainFixture.keychainClient(accountAddresses),
    _ => explorer,
    interpreter,
    _ => alreadyValidBlockCursorService
  )

  it should "synchronize on given parameters" in IOAssertion {

    val interpreter = new InterpreterClientMock

    interpreter.getSavedTransaction(accountIdentifier.id) shouldBe empty

    val syncParams = mkSyncParams(None)

    for {
      _ <- worker(interpreter).run(syncParams)
    } yield {
      val txs: List[TransactionView] = interpreter.getSavedTransaction(accountIdentifier.id)
      txs should have size 4
    }
  }

  it should "not import confirmed txs when the cursor is already on the last mined block" in IOAssertion {

    val lastMinedBlock = blockchain.last._1
    val interpreter    = new InterpreterClientMock
    val explorer =
      new ExplorerClientMock(blockchain.flatMap(_._2).toMap)

    interpreter.getSavedTransaction(accountIdentifier.id) shouldBe empty

    val syncParams = mkSyncParams(Some(lastMinedBlock))

    for {
      _ <- worker(interpreter, explorer).run(syncParams)
    } yield {

      interpreter.getSavedTransaction(accountIdentifier.id) shouldBe empty
      explorer.getConfirmedTransactionsCount = 0
    }
  }

  it should "try to import unconfirmed txs even if blockchain last block is synced" in {

    val lastMinedBlock = blockchain.last._1
    val explorer =
      new ExplorerClientMock(blockchain.flatMap(_._2).toMap, mempool.toMap)

    val syncParams = mkSyncParams(Some(lastMinedBlock))
    val w          = worker(explorer = explorer)
    for {
      _ <- w.run(syncParams)
      _ <- w.run(syncParams)
    } yield {
      explorer.getUnConfirmedTransactionsCount = 2
    }
  }

  def mkSyncParams(
      cursor: Option[BlockView]
  ): SynchronizationParameters =
    SynchronizationParameters(
      keychainId = keychainId,
      syncId = SyncId(UUID.randomUUID()),
      coin = Coin.Btc,
      blockHash = cursor.map(_.hash),
      walletUid = UUID.randomUUID()
    )
}
