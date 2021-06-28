package co.ledger.cria

import cats.effect.{ExitCode, IO}
import co.ledger.cria.AppIT.{RegisterRequest, SyncResult, TestCase}
import co.ledger.cria.common.utils.ContainerFlatSpec
import co.ledger.cria.models.Sort
import co.ledger.cria.models.account.{Account, Coin, CoinFamily, Scheme}
import co.ledger.cria.models.keychain.AccountKey.Xpub
import co.ledger.cria.utils.IOAssertion
import io.circe.Decoder
import co.ledger.cria.utils.CoinImplicits._
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import co.ledger.cria.models.circeImplicits._

import java.util.UUID
import scala.io.Source
import io.circe.parser.decode

class AppIT extends ContainerFlatSpec {

  readTestCases().foreach { tc =>
    val request = tc.registerRequest

    "App" should s"perform correct sync on account ${request.accountKey.extendedPublicKey}" in IOAssertion {
      for {
        keychainId <- makeKeychainId(request)
        args = makeArgs(request, keychainId)
        exitCode <- App.run(args, conf)
        actual   <- getSyncResult(keychainId, request.coin)
      } yield {
        assert(exitCode == ExitCode.Success)
        assert(actual == tc.expected)
      }
    }
  }

  def readTestCases(): List[TestCase] =
    List(
      "test-accounts-btc.json",
      // TODO Uncomment me when the explorers are stable (maybe ?)
      //"test-accounts-btc_testnet.json",
      "test-accounts-ltc.json"
    ).flatMap(readJson[List[TestCase]])

  def makeKeychainId(request: RegisterRequest): IO[UUID] =
    testResources
      .use(tr =>
        tr.keychainClient
          .create(request.accountKey, request.scheme, request.lookaheadSize, request.coin.toNetwork)
      )
      .map(_.keychainId)

  def makeArgs(request: RegisterRequest, keychainId: UUID): List[String] =
    List(
      ("--keychainId", keychainId.toString),
      ("--coin", request.coin),
      ("--syncId", request.syncId),
      ("--walletUid", request.walletUid)
    ).flatMap { case (name, arg) => List(name, arg.toString) }

  def getSyncResult(keychainId: UUID, coin: Coin): IO[SyncResult] = testResources.use { res =>
    val account = Account(keychainId.toString, CoinFamily.Bitcoin, coin)
    for {
      opsSize   <- res.testUtils.getOperations(account.id, 20, Sort.Ascending, None)
      utxosSize <- res.testUtils.getUtxos(account.id, 20, 0, Sort.Ascending)
      balance   <- res.testUtils.getBalance(account.id)
    } yield SyncResult(
      opsSize.total,
      utxosSize.total,
      balance.balance.longValue,
      balance.received.longValue,
      balance.sent.longValue
    )
  }

  private def readJson[A: Decoder](file: String): A = {
    val raw = Source.fromResource(file).getLines().foldLeft("")(_ + _)
    decode[A](raw)
      .fold(err => throw new IllegalArgumentException(s"Could not parse $file", err), identity)
  }
}

object AppIT {
  case class TestCase(registerRequest: RegisterRequest, expected: SyncResult)

  case class RegisterRequest(
      accountKey: Xpub,
      scheme: Scheme,
      lookaheadSize: Int,
      coin: Coin,
      syncId: UUID,
      walletUid: UUID
  )

  case class SyncResult(
      opsSize: Int,
      utxosSize: Int,
      balance: Long,
      amountReceived: Long,
      amountSent: Long
  )

  implicit val decoderXpub: Decoder[Xpub] = deriveConfiguredDecoder[Xpub]

  implicit val decoderRegisterRequest: Decoder[RegisterRequest] =
    deriveConfiguredDecoder[RegisterRequest]

  implicit val decoderSyncResult: Decoder[SyncResult] = deriveConfiguredDecoder[SyncResult]

  implicit val decoderTestCase: Decoder[TestCase] = deriveConfiguredDecoder[TestCase]
}
