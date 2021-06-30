package co.ledger.cria.domain.services

import cats.effect.{IO, Timer}
import co.ledger.cria.clients.explorer.ExplorerClient
import co.ledger.cria.clients.explorer.types.Block
import co.ledger.cria.domain.adapters.explorer.TypeHelper
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.{Account, AccountId}
import co.ledger.cria.domain.models.interpreter.SyncId
import co.ledger.cria.domain.services.interpreter.Interpreter
import org.http4s.client.UnexpectedStatus

trait CursorStateService[F[_]] {
  def getLastValidState(account: Account, block: Block, syncId: SyncId): F[Block]
}

object CursorStateService {
  def apply(
      explorerClient: ExplorerClient,
      interpreterClient: Interpreter
  )(implicit t: Timer[IO]): CursorStateService[IO] = new CursorStateService[IO]
    with ContextLogging {

    /* This method checks if the provided block is valid by calling "explorerClient.getBlock()"
     * If it is, the block is returned and used for the next sync
     * It it isn't , the last 20 known blocks are queried to the interpreter for this account,
     * and for each block in reverse order, we check if it's a valid block.
     * The first valid block found this way is returned for the sync.
     */
    def getLastValidState(account: Account, block: Block, syncId: SyncId): IO[Block] = {

      implicit val lc: CriaLogContext =
        CriaLogContext().withAccount(account).withCorrelationId(syncId)

      /*
       * Unfortunately (for now), the signature of the explorer is not set in stone and recent changes made us rework this part.
       * To be sure, we now support 2 signatures :
       * - In both cases a valid hash returns a 200 with a list of blocks
       * - An unknown valid hash return either a 404, or an empty list
       * - An invalid hash returns either a 400 or a 500 error.
       */

      explorerClient
        .getBlock(block.hash)
        .flatMap {
          case Some(lvb) => IO.pure(lvb)
          case None      => fetchLastBlocksUntilValid(account.id, block)
        }
        .handleErrorWith {
          case serverError: UnexpectedStatus if serverError.status.code != 404 =>
            logUnexpectedError(block, serverError)
          case notFoundError: UnexpectedStatus if notFoundError.status.code == 404 =>
            fetchLastBlocksUntilValid(account.id, block)
        }
    }

    private def fetchLastBlocksUntilValid(accountId: AccountId, block: Block)(implicit
        lc: CriaLogContext
    ): IO[Block] = {
      for {
        _ <- log.info(
          s"Block [hash: '${block.hash}', height: ${block.height}] has been invalidated, searching last known valid block."
        )
        blockViews <- interpreterClient.getLastBlocks(accountId)
        blocks = blockViews.map(TypeHelper.block.toExplorer)
        lastValidBlock <- getlastValidBlockRec(blocks)
        _ <- log.info(
          s"Block [hash: '${lastValidBlock.hash}', height: ${lastValidBlock.height}] is valid !"
        )
      } yield lastValidBlock
    }

    private def getlastValidBlockRec(blocks: List[Block])(implicit lc: CriaLogContext): IO[Block] =
      blocks match {
        case Nil => IO.raiseError(new Exception("no valid block found in the last blocks..."))
        case block :: tail =>
          log.info(s"Testing block [hash: '${block.hash}', height: ${block.height}]") *>
            explorerClient
              .getBlock(block.hash)
              .flatMap {
                case Some(lvb) => IO.pure(lvb)
                case None      => getlastValidBlockRec(tail)
              }
              .handleErrorWith {
                case serverError: UnexpectedStatus if serverError.status.code != 404 =>
                  logUnexpectedError(block, serverError)
                case notFoundError: UnexpectedStatus if notFoundError.status.code == 404 =>
                  getlastValidBlockRec(tail)
              }
      }

    private def logUnexpectedError(b: Block, serverError: UnexpectedStatus)(implicit
        lc: CriaLogContext
    ): IO[Block] = {
      log.error(
        s"Error ${serverError.status.code} while calling explorer with block : ${b.hash}"
      ) *>
        IO.raiseError(serverError)
    }
  }
}
