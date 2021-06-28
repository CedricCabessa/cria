package co.ledger.cria

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.clients.grpc.KeychainClient
import co.ledger.cria.clients.http.ExplorerClient.Address
import co.ledger.cria.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.cria.models.keychain.{AccountKey, BitcoinLikeNetwork, BitcoinNetwork, KeychainInfo}
import java.util.UUID

import co.ledger.cria.models.account.Scheme

import scala.collection.mutable

object KeychainFixture {

  trait UsedAddressesTracker {
    val newlyMarkedAddresses: mutable.Map[Address, Address] = mutable.Map.empty
  }

  def keychainClient(
      addresses: LazyList[Address],
      lookaheadSize: Int = 20
  ): KeychainClient with UsedAddressesTracker =
    new KeychainClient with UsedAddressesTracker {

      override def create(
          accountKey: AccountKey,
          scheme: Scheme,
          lookaheadSize: Int,
          network: BitcoinLikeNetwork
      ): IO[KeychainInfo] =
        IO.delay(
          KeychainInfo(
            UUID.randomUUID(),
            "",
            "",
            "",
            "",
            lookaheadSize,
            scheme,
            network
          )
        )

      override def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
        IO.delay(
          KeychainInfo(
            keychainId,
            externalDescriptor = "externalDesc",
            internalDescriptor = "internalDesc",
            extendedPublicKey = "extendedPublicKey",
            slip32ExtendedPublicKey = "slip32ExtendedPublicKey",
            lookaheadSize = lookaheadSize,
            scheme = Scheme.Bip44,
            network = BitcoinNetwork.MainNet
          )
        )

      override def getAddresses(
          keychainId: UUID,
          fromIndex: Int,
          toIndex: Int,
          changeType: Option[ChangeType]
      ): IO[List[AccountAddress]] =
        IO.delay(
          addresses
            .slice(fromIndex, toIndex)
            .map(AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1)))
            .toList
        )

      override def markAddressesAsUsed(keychainId: UUID, addresses: List[String]): IO[Unit] = {
        addresses.foreach(a => newlyMarkedAddresses.update(a, a))
        IO.unit
      }

      override def getKnownAndNewAddresses(
          keychainId: UUID,
          changeType: Option[ChangeType]
      ): IO[List[AccountAddress]] =
        for {
          knownAddresses <- IO(
            newlyMarkedAddresses.keys.toList.map(
              AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1))
            )
          )

          newAddresses <- getAddresses(
            keychainId,
            newlyMarkedAddresses.size - 1,
            newlyMarkedAddresses.size + lookaheadSize - 1
          )

        } yield knownAddresses ++ newAddresses

      override def getFreshAddresses(
          keychainId: UUID,
          change: ChangeType,
          size: Int
      ): IO[List[AccountAddress]] = ???

      override def getAddressesPublicKeys(
          keychainId: UUID,
          derivations: List[List[Int]]
      ): IO[List[String]] = ???

      override def deleteKeychain(keychainId: UUID): IO[Unit] = ???

      override def resetKeychain(keychainId: UUID): IO[Unit] = ???

    }

}
