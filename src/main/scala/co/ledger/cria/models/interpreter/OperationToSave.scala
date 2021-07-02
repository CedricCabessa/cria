package co.ledger.cria.models.interpreter

import java.time.Instant
import cats.effect.IO
import co.ledger.cria.models.circeImplicits._
import co.ledger.cria.models._
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.account.AccountId
import fs2.Stream
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

case class OperationToSave(
    uid: Operation.UID,
    accountId: AccountId,
    hash: String,
    operationType: OperationType,
    value: BigInt,
    fees: BigInt,
    time: Instant,
    blockHash: Option[String],
    blockHeight: Option[Long]
)

object OperationToSave {
  implicit val encoder: Encoder[OperationToSave] =
    deriveConfiguredEncoder[OperationToSave]
  implicit val decoder: Decoder[OperationToSave] =
    deriveConfiguredDecoder[OperationToSave]
}

case class TransactionAmounts(
    accountId: AccountId,
    hash: String,
    blockHash: Option[String],
    blockHeight: Option[Long],
    blockTime: Option[Instant],
    fees: BigInt,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) extends ContextLogging {

  def computeOperations(implicit lc: CriaLogContext): List[OperationToSave] = {
    TransactionType.fromAmounts(inputAmount, outputAmount, changeAmount) match {
      case SendType =>
        List(makeOperationToSave(inputAmount - changeAmount, OperationType.Send))
      case ReceiveType =>
        List(makeOperationToSave(outputAmount + changeAmount, OperationType.Receive))
      case ChangeOnlyType =>
        List(makeOperationToSave(changeAmount, OperationType.Receive))
      case BothType =>
        List(
          makeOperationToSave(inputAmount - changeAmount, OperationType.Send),
          makeOperationToSave(outputAmount, OperationType.Receive)
        )
      case NoneType =>
        log
          .error(
            s"Error on tx : $hash, no transaction type found for amounts : input: $inputAmount, output: $outputAmount, change: $changeAmount"
          )
          .unsafeRunSync()
        Nil

    }
  }

  private def makeOperationToSave(amount: BigInt, operationType: OperationType) = {
    OperationToSave(
      Operation
        .uid(accountId, Operation.TxId(hash), operationType, blockHeight),
      accountId = accountId,
      hash = hash,
      operationType = operationType,
      value = amount,
      time = blockTime.getOrElse(Instant.now()),
      blockHash = blockHash,
      blockHeight = blockHeight,
      fees = fees
    )
  }
}
