package co.ledger.cria.models

import java.time.Instant
import java.util.UUID

import cats.data.NonEmptyList
import co.ledger.cria.models.account.{AccountId, WalletUid}
import co.ledger.cria.models.interpreter.{ChangeType, Operation, OperationType, TransactionView}
import co.ledger.cria.models.interpreter._
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

  implicit val doobieMetaAccountId: Meta[AccountId] =
    Meta[UUID].timap[AccountId](AccountId(_))(_.value)

  implicit val doobieMetaWalletUid: Meta[WalletUid] =
    Meta[UUID].timap[WalletUid](WalletUid(_))(_.value)

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

  implicit lazy val writeWDOperation: Write[WDOperation] =
    Write[
      (
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          Option[String],
          String,
          String
      )
    ].contramap { op =>
      (
        op.uid,
        op.accountUid,
        op.walletUid,
        op.operationType,
        op.date,
        op.senders,
        op.recipients,
        op.amount,
        op.fees,
        op.blockUid,
        op.currencyName,
        op.trust
      )
    }

}
