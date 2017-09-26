package scorex.waves.http

import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import com.wavesplatform.UtxPool
import com.wavesplatform.settings.RestAPISettings
import io.netty.channel.group.ChannelGroup
import io.swagger.annotations._
import scorex.BroadcastRoute
import scorex.account.Address
import scorex.api.http._
import scorex.api.http.assets.PaymentRequest
import scorex.crypto.encode.Base58
import scorex.transaction.{PaymentTransaction, TransactionFactory}
import scorex.utils.Time
import scorex.wallet.Wallet
import scorex.waves.transaction.SignedPaymentRequest

import scala.concurrent.ExecutionContext.Implicits.global

@Path("/waves")
@Api(value = "waves")
@Deprecated
case class WavesApiRoute(settings: RestAPISettings, wallet: Wallet, utx: UtxPool, allChannels: ChannelGroup, time: Time)
  extends ApiRoute with BroadcastRoute {

  override lazy val route = pathPrefix("waves") {
    externalPayment ~ signPayment ~ broadcastSignedPayment ~ payment ~ createdSignedPayment
  }

  @Deprecated
  @Path("/payment")
  @ApiOperation(value = "Send payment from wallet. Deprecated: use /assets/transfer instead",
    notes = "Send payment from wallet to another wallet. Each call sends new payment. Deprecated: use /assets/transfer instead",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.api.http.assets.PaymentRequest",
      defaultValue = "{\n\t\"amount\":400,\n\t\"fee\":1,\n\t\"sender\":\"senderId\",\n\t\"recipient\":\"recipientId\"\n}"
    )
  ))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "Json with response or error")))
  def payment: Route = withAuth {
    (path("payment") & post) {
      json[PaymentRequest] { payment =>
        doBroadcast(TransactionFactory.createPayment(payment, wallet, time)) map { f =>
          f.map { t =>
            val tx = t.asInstanceOf[PaymentTransaction]
            SignedPaymentRequest(tx.timestamp, tx.amount, tx.fee, tx.recipient.address,
              Base58.encode(tx.sender.publicKey.getEncoded), tx.sender.address, tx.signature.base58)
          }
        }
      }
    }
  }

  @Deprecated
  @Path("/payment/signature")
  @ApiOperation(value = "Create payment signed by address from wallet",
    notes = "Create unique payment signed by address from wallet. Without broadcasting to network.",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.api.http.assets.PaymentRequest",
      defaultValue = "{\n\t\"amount\":400,\n\t\"fee\":1,\n\t\"sender\":\"senderId\",\n\t\"recipient\":\"recipientId\"\n}"
    )
  ))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "Json with response or error")))
  def signPayment: Route = (post & path("payment" / "signature")) {
    json[PaymentRequest] { payment =>
      (for {
        sender <- wallet.findWallet(payment.sender)
        recipient <- Address.fromString(payment.recipient)
        pt <- PaymentTransaction.create(sender, recipient, payment.amount, payment.fee, time.correctedTime())
      } yield pt)
        .left.map(ApiError.fromValidationError)
        .map { t =>
          SignedPaymentRequest(t.timestamp, t.amount, t.fee, t.recipient.address, Base58.encode(t.sender.publicKey.getEncoded), t.sender.address, t.signature.base58)
        }
    }
  }

  @Deprecated
  @Path("/create-signed-payment")
  @ApiOperation(value = "Sign payment",
    notes = "Sign payment by provided wallet seed",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.waves.http.UnsignedPayment"
    )
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json with response or error")
  ))
  def createdSignedPayment: Route = post {
    path("create-signed-payment") {
      json[UnsignedPayment] { payment =>
        val senderAccount = Wallet.generateNewAccount()
        for {
          recipientAccount <- Address.fromString(payment.recipient).left.map(ApiError.fromValidationError)
          _tx <- PaymentTransaction.create(senderAccount, recipientAccount, payment.amount, payment.fee, payment.timestamp)
            .left.map(ApiError.fromValidationError)
        } yield SignedPaymentRequest(_tx.timestamp, _tx.amount, _tx.fee, _tx.recipient.address, Base58.encode(_tx.sender.publicKey.getEncoded),
          _tx.sender.address, _tx.signature.base58)
      }
    }
  }

  @Deprecated
  @Path("/external-payment")
  @ApiOperation(value = "Broadcast payment", notes = "Publish signed payment to the Blockchain", httpMethod = "POST", produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.waves.transaction.SignedPaymentRequest",
      defaultValue = "{\n\t\"timestamp\": 0,\n\t\"amount\":400,\n\t\"fee\":1,\n\t\"senderPublicKey\":\"senderPubKey\",\n\t\"recipient\":\"recipientId\",\n\t\"signature\":\"sig\"\n}"
    )
  ))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "Json with response or error")))
  def externalPayment: Route = broadcastPaymentRoute("external-payment")

  @Deprecated()
  @Path("/broadcast-signed-payment")
  @ApiOperation(value = "Broadcast signed payment. Deprecated: use /assets/broadcast/transfer instead",
    notes = "Publish signed payment to the Blockchain. Deprecated: use /assets/broadcast/transfer instead",
    httpMethod = "POST", produces = "application/json", consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.waves.transaction.SignedPaymentRequest",
      defaultValue = "{\n\t\"timestamp\": 0,\n\t\"amount\":400,\n\t\"fee\":1,\n\t\"senderPublicKey\":\"senderPubKey\",\n\t\"senderAddress\":\"senderAddress\",\n\t\"recipient\":\"recipientId\",\n\t\"signature\":\"sig\"\n}"
    )
  ))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "Json with response or error")))
  def broadcastSignedPayment: Route = broadcastPaymentRoute("broadcast-signed-payment")

  private def broadcastPaymentRoute(suffix: String): Route = (path(suffix) & post) {
    json[SignedPaymentRequest] { payment =>
      doBroadcast(TransactionFactory.broadcastPayment(payment))
    }
  }
}
