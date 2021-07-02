package co.ledger.cria.models.interpreter

case class WDTxToSave(
    block: Option[WDBlock],
    tx: WDTransaction,
    ops: List[WDOperation]
)
