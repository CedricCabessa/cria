package co.ledger.cria.clients.protocol.grpc

object Exceptions {

  case class GrpcClientException(t: Throwable, clientName: String)
      extends Exception(s"$clientName - ${t.getMessage}", t)

  case object MalformedProtobufUuidException extends Exception("Invalid UUID on protobuf request")

}
