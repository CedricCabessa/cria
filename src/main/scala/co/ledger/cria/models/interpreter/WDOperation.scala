package co.ledger.cria.models.interpreter

import java.math.BigInteger
import java.security.MessageDigest

import co.ledger.cria.models.account.Coin
import co.ledger.cria.services.interpreter.TransactionQueries.TransactionDetails

case class WDOperation(
    uid: String,
    accountUid: String,
    walletUid: String,
    operationType: String,
    date: String,
    senders: String,
    recipients: String,
    amount: String,
    fees: String,
    blockUid: Option[String],
    currencyName: String,
    trust: String,
    txUid: String,
    txHash: String
) {}

object WDOperation {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromOperation(
      operation: OperationToSave,
      coin: Coin,
      tx: WDTransaction,
      walletUid: String
  ): WDOperation =
    WDOperation(
      uid = computeUid(
        operation.accountId.toString,
        operation.hash,
        operation.operationType.name.toUpperCase
      ),
      accountUid = operation.accountId.toString,
      walletUid = walletUid,
      operationType = operation.operationType.name.toUpperCase,
      date = operation.time.toString,
      senders = tx.inputs.map(_.address).mkString(","),
      recipients = tx.outputs.map(_.address).mkString(","),
      amount = (operation.value - operation.fees).toString(16),
      fees = operation.fees.toString(16),
      blockUid = tx.blockUid,
      currencyName = coin.name,
      trust = "",
      txUid = tx.uid,
      txHash = operation.hash
    )

  def computeUid(accountUid: String, txHash: String, operationType: String): String = {
    String.format(
      "%032x",
      new BigInteger(
        1,
        digester.digest(s"uid:$accountUid+$txHash+$operationType".getBytes("UTF-8"))
      )
    )
  }

}
