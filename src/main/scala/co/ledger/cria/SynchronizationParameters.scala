package co.ledger.cria

import java.util.UUID

import co.ledger.cria.models.account.{Coin, WalletUid}
import co.ledger.cria.models.interpreter.SyncId
import co.ledger.cria.models.keychain.KeychainId

case class SynchronizationParameters(
    keychainId: KeychainId,
    coin: Coin,
    syncId: SyncId,
    blockHash: Option[String],
    walletUid: WalletUid
)
