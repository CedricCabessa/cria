package co.ledger.cria.models.interpreter

import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID

import co.ledger.cria.models.account.{AccountId, Coin}

case class WDTransaction(
    uid: String,
    hash: String,
    version: String,
    blockUid: Option[String],
    time: String,
    locktime: String,
    inputs: List[WDInput],
    outputs: List[WDOutput]
)

object WDTransaction {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromTransactionView(
      accountId: AccountId,
      tx: TransactionView,
      block: Option[WDBlock],
      coin: Coin
  ): WDTransaction = {
    val accountIdString = accountId.value.toString
    WDTransaction(
      uid = createUid(accountIdString, tx.hash),
      hash = tx.hash,
      version = ???,
      blockUid = block.map(_.uid),
      tx.receivedAt.toString,
      tx.lockTime.toString,
      tx.inputs.toList.map(WDInput.fromInput(_, accountIdString)),
      tx.outputs.toList.map(WDOutput.fromOutput(_, accountIdString))
    )
  }

  def createUid(accountUid: String, txHash: String) = {
    String.format(
      "%032x",
      new BigInteger(
        1,
        digester.digest(s"uid:$accountUid+$txHash".getBytes("UTF-8"))
      )
    )
  }
}

case class WDInput(
    uid: String,
    previousOutputIdx: Int,
    previousTxHash: String,
    previousTxUid: String,
    amount: BigInt,
    address: String,
    coinbase: String,
    sequence: Long
)

object WDInput {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromInput(input: InputView, accountId: String): WDInput =
    WDInput(
      uid = createUid(
        accountUid = accountId,
        outputIndex = input.outputIndex,
        previousTxHash = input.outputHash,
        coinbase = ???
      ),
      previousOutputIdx = input.outputIndex,
      previousTxHash = input.outputHash,
      previousTxUid = ???,
      amount = input.value,
      address = input.address,
      coinbase = ???,
      sequence = input.sequence
    )

  def createUid(accountUid: String, outputIndex: Int, previousTxHash: String, coinbase: String) = {
    String.format(
      "%032x",
      new BigInteger(
        1,
        digester.digest(s"uid:$accountUid+$outputIndex+$previousTxHash+$coinbase".getBytes("UTF-8"))
      )
    )
  }
}

case class WDOutput(
    idx: Int,
    amount: BigInt,
    script: String,
    address: String,
    accountUid: String,
    blockHeight: Long,
    replaceable: Int = 0
)

object WDOutput {

  def fromOutput(output: OutputView, accountId: String) =
    WDOutput(
      output.outputIndex,
      amount = output.value,
      script = output.scriptHex,
      address = output.address,
      accountUid = accountId,
      blockHeight = ???
    )

}
