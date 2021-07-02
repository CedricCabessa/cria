package co.ledger.cria.services.interpreter

import cats.implicits._

import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.models.interpreter._
import co.ledger.cria.models.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

object WDQueries extends DoobieLogHandler {

  def saveWDOperation(op: WDOperation): doobie.ConnectionIO[Int] = {
    val operationQuery =
      """INSERT INTO operations(
        uid,
        account_uid,
        wallet_uid,
        type,
        date,
        senders,
        recipients,
        amount,
        fees,
        block_uid,
        currency_name,
        trust
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    val operationLinkQuery =
      s"""INSERT INTO bitcoin_operations(uid, transaction_uid, transaction_hash) VALUES(${op.uid}, ${op.txUid}, ${op.txHash})
      ON CONFLICT DO NOTHING"""

    Update[WDOperation](operationQuery)
      .run(op)
      .flatMap(_ => Fragment.const(operationLinkQuery).update.run)
  }

  def saveTransaction(tx: WDTransaction): doobie.ConnectionIO[Int] = {
    val txQuery =
      """INSERT INTO bitcoin_transaction(
        uid,
        hash,
        version,
        block_uid,
        time,
        locktime
      ) VALUES (?, ?, ?, ?, ?, ?)
    """

    Update[WDTransaction](txQuery).run(tx)
  }

  def saveInputs(tx: WDTransaction, inputs: List[WDInput]) = {
    val inputsQuery =
      """INSERT INTO bitcoin_inputs(
        uid,
        previous_output_idx,
        previous_tx_hash,
        previous_tx_uid,
        amount,
        address,
        coinbase,
        sequence
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """

    val links = inputs.map { i =>
      val inputsLinkQuery =
        s"""INSERT INTO bitcoin_transaction_inputs(
        transaction_uid, 
        transaction_hash, 
        input_uid, 
        input_idx, 
      ) VALUES (${tx.uid}, ${tx.hash}, ${i.uid}, ${i.previousOutputIdx})
    """

      Fragment.const(inputsLinkQuery).update.run
    }

    links.sequence.flatMap(_ => Update[WDInput](inputsQuery).updateMany(inputs))
  }

  def saveOutputs(
      outputs: List[WDOutput],
      txUid: String,
      txHash: String
  ): doobie.ConnectionIO[Int] = {
    val outputsQuery =
      s"""INSERT INTO bitcoin_outputs(
        idx,
        transaction_uid,
        transaction_hash,
        amount,
        script,
        address,
        account_uid,
        block_height,
        replaceable
      ) VALUES (?, $txUid, $txHash, ?, ?, ?, ?, ?, ?)
    """

    Update[WDOutput](outputsQuery).updateMany(outputs)
  }

  def saveBlocks(blocks: List[WDBlock]): doobie.ConnectionIO[Int] = {
    val blocksQuery =
      """INSERT INTO blocks(
        uid,
        hash,
        height,
        time,
        currency_name
      ) VALUES (?, ?, ?, ?, ?)
    """

    Update[WDBlock](blocksQuery).updateMany(blocks)
  }

  def deleteTransaction(hash: String): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM operations WHERE txHash = $hash AND date >= :date""".update.run *>

    sql"""DELETE FROM bitcoin_inputs WHERE uid IN (
      SELECT input_uid FROM bitcoin_transaction_inputs
      WHERE transaction_uid = $hash
      )
    """.update.run *>

    sql"""DELETE FROM bitcoin_transactions
    WHERE hash = $hash""".update.run
  }

}
