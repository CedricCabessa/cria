package co.ledger.cria

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.grpc.mocks.{InterpreterClientMock, KeychainClientMock}
import co.ledger.cria.clients.http.ExplorerHttpClient
import co.ledger.cria.SynchronizationResult.SynchronizationSuccess
import co.ledger.cria.config.Config
import co.ledger.cria.services.CursorStateService
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import java.time.Instant
import java.util.UUID

import co.ledger.cria.clients.Clients
import co.ledger.cria.models.account.{Account, Coin, CoinFamily}
import co.ledger.cria.utils.IOAssertion

import scala.concurrent.ExecutionContext

class SynchronizerIT extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  IOAssertion {
    Clients.htt4s
      .use { httpClient =>
        val keychainId = UUID.randomUUID()

        val keychainClient = new KeychainClientMock

        val explorerClient = new ExplorerHttpClient(httpClient, conf.explorer, _)

        val interpreterClient = new InterpreterClientMock

        val cursorStateService: Coin => CursorStateService[IO] =
          c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _, _)

        val args: SynchronizationParameters =
          SynchronizationParameters(
            keychainId,
            Coin.Btc,
            UUID.randomUUID(),
            None,
            UUID.randomUUID()
          )

        val worker = new Synchronizer(
          keychainClient,
          explorerClient,
          interpreterClient,
          cursorStateService
        )

        val account = Account(
          keychainId.toString,
          CoinFamily.Bitcoin,
          Coin.Btc
        )

        worker
          .run(args)
          .map { result =>
            it should "have 35 used addresses for the account" in {
              keychainClient.usedAddresses.size shouldBe 19 + 35
            }

            val expectedTxsSize         = 73
            val expectedLastBlockHeight = 644553L

            it should s"have synchronized $expectedTxsSize txs with last blockHeight > $expectedLastBlockHeight" in {
              interpreterClient.getSavedTransaction(account.id) should have size expectedTxsSize

              result match {
                case SynchronizationSuccess(_, newCursor) =>
                  newCursor.height should be > expectedLastBlockHeight
                  newCursor.time should be > Instant.parse("2020-08-20T13:01:16Z")
                case _ =>
                  fail("synchronization should succeed")
              }
            }
          }
      }
  }

}