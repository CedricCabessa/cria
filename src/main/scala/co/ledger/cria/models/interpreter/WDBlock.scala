package co.ledger.cria.models.interpreter

import java.math.BigInteger
import java.security.MessageDigest

import co.ledger.cria.models.account.Coin

case class WDBlock(
    uid: String,
    hash: String,
    height: Long,
    time: String,
    currencyName: String
)

object WDBlock {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromBlock(block: BlockView, coin: Coin): WDBlock =
    WDBlock(
      uid = computeUid(block.hash, coin),
      hash = block.hash,
      height = block.height,
      time = block.time.toString,
      currencyName = coin.name
    )

  def computeUid(hash: String, coin: Coin): String = {
    String.format(
      "%032x",
      new BigInteger(
        1,
        digester.digest(s"uid:$hash+${coin.name}".getBytes("UTF-8"))
      )
    )
  }
}
