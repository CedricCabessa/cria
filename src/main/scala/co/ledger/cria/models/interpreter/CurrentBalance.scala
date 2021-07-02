package co.ledger.cria.models.interpreter

case class BlockchainBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    sent: BigInt
)

case class CurrentBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    sent: BigInt,
    unconfirmedBalance: BigInt
)
