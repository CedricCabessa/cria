package co.ledger.cria.clients.grpc.mocks

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.models.interpreter.ChangeType.External
import co.ledger.cria.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.cria.models.keychain.{AccountKey, BitcoinLikeNetwork, BitcoinNetwork, KeychainInfo}
import co.ledger.cria.clients.grpc.KeychainClient
import co.ledger.cria.models.account.Scheme

import scala.collection.mutable

class KeychainClientMock(createdKeychainId: Option[UUID] = None) extends KeychainClient {

  var usedAddresses: mutable.Seq[String] = mutable.Seq.empty

  private val derivations: NonEmptyList[Int] = NonEmptyList(1, List(0))
  private val change: External.type          = ChangeType.External

  val derivedAddresses: List[AccountAddress] = List(
    AccountAddress("1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ", change, derivations),
    AccountAddress("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ", change, derivations),
    AccountAddress("1MfeDvj5AUBG4xVMrx1xPgmYdXQrzHtW5b", change, derivations),
    AccountAddress("1GgX4cGLiqF9p4Sd1XcPQhEAAhNDA4wLYS", change, derivations),
    AccountAddress("1Q2Bv9X4yCTNn1P1tmFuWpijHvT3xYt3F", change, derivations),
    AccountAddress("1G7g5zxfjWCSJRuNKVasVczrZNowQRwbij", change, derivations),
    AccountAddress("1MFjwXsibXbvVzkE4chJrhbczDivpbbVTE", change, derivations),
    AccountAddress("1HFzpigeFDZGp45peU4NAHLgyMxiGj1GzT", change, derivations),
    AccountAddress("17xsjFyLgbWrjauC8F5hyaaaWdf6L6Y6L4", change, derivations),
    AccountAddress("1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd", change, derivations),
    AccountAddress("1Mj9jzHtAyVvM9Y274LCcfLBBBfwRiDK9V", change, derivations),
    AccountAddress("1Ng5FPQ1rUbEHak8Qcjy6BRJhjF1n3AVR6", change, derivations),
    AccountAddress("145Tdk8ntZQa5kgyLheL835z6yukHjbEKF", change, derivations),
    AccountAddress("16hG8pC6D4gRmRvfHT3zHGcED9FMocN4hG", change, derivations),
    AccountAddress("1NQd72r3kUESTAMvDjaJU1Gk842HPcPVQQ", change, derivations),
    AccountAddress("1JiBkCdhc3P4by29kLzraz4CuwjAvTA96H", change, derivations),
    AccountAddress("1MXLmPcLRoQAWZqfgxtvhvUWLDQ3We2sUJ", change, derivations),
    AccountAddress("1DRCwCw8HjeRsRi4wyfJzqgBeNBJTdvvx1", change, derivations),
    AccountAddress("1NTG6NWQq1DZYZf8VQ58FBGGDwA9deM7Aq", change, derivations),
    AccountAddress("1JMbu32pdVu6FvKbmrJMTSJSWFcJJ47JtY", change, derivations),
    AccountAddress("13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ", change, derivations),
    AccountAddress("19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm", change, derivations),
    AccountAddress("1D2R9GQu541rmUKY5kz6gjWuX2kfEusRre", change, derivations),
    AccountAddress("1B3g4WxFBJtPh6azgQdRs5f7zwXhcocELc", change, derivations),
    AccountAddress("12AdRB44ctyTaQiLgthz7WMFJ7dFNornmA", change, derivations),
    AccountAddress("1KHyosZPVXxVBaQ7qtRjPUWWt911rAkfg6", change, derivations),
    AccountAddress("1KConohwqXnB87BYpp2n7GfrPRhPqa471a", change, derivations),
    AccountAddress("1BGCPcrzx3G48eY7vhpc7UEtJbpXW3mZ1t", change, derivations),
    AccountAddress("14er8aopUkpX4KcL9rx7GU2t8zbFANQyC3", change, derivations),
    AccountAddress("1LPR9mGFJrWkiMPj2HWfnBA5weEeKV2arY", change, derivations),
    AccountAddress("15M1GcHsakzQtxkVDcw92siMk3c3Ap3C5h", change, derivations),
    AccountAddress("1GWfouhfoTHctEeUCMd1tcF2cdkfuaSXdh", change, derivations),
    AccountAddress("1CyAcL6Kd5pWzFucQE2Ev527FEQ9dTtPJ1", change, derivations),
    AccountAddress("1AxhDoozM9VfsktCKVN7kp6UkaqVq65rHF", change, derivations),
    AccountAddress("1Aj3Gi1j5UsvZh4ccjaqdnogPMWy54Z5ii", change, derivations)
  ) ++ (1 to 20).map(i => AccountAddress(s"unused$i", change, derivations))

  private val derivationsInternal: NonEmptyList[Int] = NonEmptyList(1, List(1))
  val derivedAddressesInternal: List[AccountAddress] =
    (1 to 20)
      .map(i => AccountAddress(s"changeAddr$i", ChangeType.Internal, derivationsInternal))
      .toList

  def create(
      accountKey: AccountKey,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinLikeNetwork
  ): IO[KeychainInfo] =
    IO.delay(
      KeychainInfo(
        createdKeychainId.getOrElse(UUID.randomUUID()),
        "",
        "",
        "",
        "",
        20,
        Scheme.Unspecified,
        BitcoinNetwork.Unspecified
      )
    )

  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
    IO.pure(
      KeychainInfo(
        UUID.randomUUID(),
        "",
        "",
        "",
        "",
        lookaheadSize = 20,
        Scheme.Unspecified,
        BitcoinNetwork.Unspecified
      )
    )

  def markAddressesAsUsed(keychainId: UUID, addresses: List[String]): IO[Unit] =
    IO.delay {
      usedAddresses = usedAddresses ++ addresses
    }

  def getAddresses(
      keychainId: UUID,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType]
  ): IO[List[AccountAddress]] =
    if (changeType.getOrElse(ChangeType.External) == ChangeType.External) {
      IO.delay(derivedAddresses.slice(fromIndex, toIndex))
    } else {
      IO.delay(derivedAddressesInternal.slice(fromIndex, toIndex))
    }

  def getKnownAndNewAddresses(
      keychainId: UUID,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    for {
      knownAddresses <- IO(
        derivedAddresses.filter(a => usedAddresses.contains(a.accountAddress))
      )

      newAddresses <- getAddresses(
        keychainId,
        knownAddresses.size - 1,
        knownAddresses.size + 20 - 1
      )

    } yield knownAddresses ++ newAddresses

  def getFreshAddresses(
      keychainId: UUID,
      change: ChangeType,
      size: Int
  ): IO[List[AccountAddress]] =
    if (change == ChangeType.External) {
      IO(derivedAddresses)
    } else {
      IO(derivedAddressesInternal)
    }

  override def getAddressesPublicKeys(
      keychainId: UUID,
      derivations: List[List[Int]]
  ): IO[List[String]] = ???

  override def deleteKeychain(keychainId: UUID): IO[Unit] = ???

  override def resetKeychain(keychainId: UUID): IO[Unit] = ???

}
