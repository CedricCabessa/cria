package co.ledger.cria.domain.models

import java.time.Instant
import cats.data.NonEmptyList
import co.ledger.cria.domain.models.account.AccountId
import co.ledger.cria.domain.models.interpreter.{Operation, OperationType, TransactionView}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.ChangeType
import doobie._
import doobie.postgres.implicits._

import scala.math.BigDecimal.javaBigDecimal2bigDecimal

object implicits {

  implicit def getNel[A](implicit ev: Get[List[A]]): Get[NonEmptyList[A]] =
    ev.map(NonEmptyList.fromList(_).getOrElse(sys.error("oops")))

  implicit def putNel[A](implicit ev: Put[List[A]]): Put[NonEmptyList[A]] =
    ev.contramap(_.toList)

  implicit val bigIntType: Meta[BigInt] =
    Meta.BigDecimalMeta.imap[BigInt](_.toBigInt)(BigDecimal(_).bigDecimal)

  implicit val operationTypeMeta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  implicit val changeTypeMeta: Meta[ChangeType] =
    pgEnumStringOpt("change_type", ChangeType.fromKey, _.toString.toLowerCase())

  implicit lazy val readTransactionView: Read[TransactionView] =
    Read[
      (String, String, Option[String], Option[Long], Option[Instant], Instant, Long, BigInt, Int)
    ].map {
      case (
            id,
            hash,
            blockHashO,
            blockHeightO,
            blockTimeO,
            receivedAt,
            lockTime,
            fees,
            confirmations
          ) =>
        TransactionView(
          id = id,
          hash = hash,
          receivedAt = receivedAt,
          lockTime = lockTime,
          fees = fees,
          inputs = Seq(),
          outputs = Seq(),
          block =
            blockHashO.map(blockHash => BlockView(blockHash, blockHeightO.get, blockTimeO.get)),
          confirmations = confirmations
        )
    }

  implicit lazy val readOperation: Read[Operation] =
    Read[
      (
          String,
          AccountId,
          String,
          OperationType,
          BigInt,
          BigInt,
          Instant,
          Option[Long],
          TransactionView
      )
    ].map { case (uid, accountId, hash, operationType, value, fees, time, height, tx) =>
      Operation(
        Operation.UID(uid),
        accountId,
        hash,
        tx,
        operationType,
        value,
        fees,
        time,
        height
      )
    }

}
