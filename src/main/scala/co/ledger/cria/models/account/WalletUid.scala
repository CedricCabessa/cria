package co.ledger.cria.models.account

import java.util.UUID

import scala.util.Try

case class WalletUid(value: UUID) extends AnyVal

object WalletUid {
  def fromString(value: String): Option[WalletUid] =
    Try(UUID.fromString(value)).toOption.map(WalletUid(_))

}
