package co.ledger.lama.bitcoin.worker.cli


import java.util.UUID

case class CommandLineOptions (xpub: String,
                               syncId: UUID,
                               cursor: Option[String],
                               walletId: UUID
                              )