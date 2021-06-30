package co.ledger.cria.domain.services

import java.time.Instant
import java.util.UUID
import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.clients.explorer.ExplorerHttpClient
import co.ledger.cria.clients.explorer.types.{Block, Coin, CoinFamily}
import co.ledger.cria.clients.protocol.grpc.mocks.InterpreterClientMock
import co.ledger.cria.clients.protocol.http.Clients
import co.ledger.cria.config.Config
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.domain.models.account.Account
import co.ledger.cria.domain.models.interpreter.{BlockView, SyncId, TransactionView}
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.utils.IOAssertion
import fs2._
import org.http4s.client.Client
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class CursorStateServiceIT extends AnyFlatSpecLike with Matchers with DefaultContextLogging {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  val resources: Resource[IO, Client[IO]] = Clients.htt4s

  it should "get the last valid cursor state" in IOAssertion {
    resources.use { httpClient =>
      val explorerClient     = new ExplorerHttpClient(httpClient, conf.explorer, Coin.Btc)
      val interpreterClient  = new InterpreterClientMock
      val cursorStateService = CursorStateService(explorerClient, interpreterClient)

      val keychainId = KeychainId(UUID.randomUUID())
      val account =
        Account(keychainId, CoinFamily.Bitcoin, Coin.Btc)
      val accountId = account.id
      val syncId    = SyncId(UUID.randomUUID())

      val lastValidHash   = "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379"
      val lastValidHeight = 559033L
      val invalidHash     = "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608376"

      for {
        // save transactions to create "blocks" in the interpreter
        _ <-
          Stream(
            createTx(
              "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608371", //invalid
              559035L
            ),
            createTx(
              "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608372", //invalid
              559034L
            ),
            createTx(
              "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379", //last valid
              559033L
            ),
            createTx(
              "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608373", //invalid
              559032L
            ),
            createTx(
              "0000000000000000000bf68b57eacbff287ceafecb54a30dc3fd19630c9a3883", //valid but not last
              559031L
            )
          ).through(interpreterClient.saveTransactions(accountId)).compile.drain

        block <- cursorStateService.getLastValidState(
          account,
          Block(invalidHash, 0L, Instant.now()),
          syncId
        )
      } yield {
        block.hash shouldBe lastValidHash
        block.height shouldBe lastValidHeight
      }

    }
  }

  private def createTx(blockHash: String, height: Long) =
    TransactionView(
      "id",
      "hash",
      Instant.now(),
      0L,
      1,
      Nil,
      Nil,
      Some(BlockView(blockHash, height, Instant.now())),
      0
    )

}
