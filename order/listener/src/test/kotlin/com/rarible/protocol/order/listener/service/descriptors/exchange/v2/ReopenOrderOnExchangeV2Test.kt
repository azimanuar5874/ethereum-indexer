package com.rarible.protocol.order.listener.service.descriptors.exchange.v2

import com.rarible.core.common.nowMillis
import com.rarible.core.test.wait.Wait
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.dto.PrepareOrderTxFormDto
import com.rarible.protocol.order.core.model.*
import com.rarible.protocol.order.core.service.PrepareTxService
import com.rarible.protocol.order.listener.integration.IntegrationTest
import com.rarible.protocol.order.listener.misc.setField
import com.rarible.protocol.order.listener.misc.sign
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import scalether.domain.request.Transaction
import java.math.BigInteger

@IntegrationTest
class ReopenOrderOnExchangeV2Test : AbstractExchangeV2Test() {

    @Autowired
    private lateinit var prepareTxService: PrepareTxService

    @Test
    internal fun `user sells item then buys it back and then sells it again`() = runBlocking {
        setField(prepareTxService, "eip712Domain", eip712Domain)

        val tokenId = EthUInt256.ONE
        token721.mint(userSender1.from(), tokenId.value, "").execute().verifySuccess()
        token1.mint(userSender1.from(), BigInteger.valueOf(200)).execute().verifySuccess()
        token2.mint(userSender2.from(), BigInteger.valueOf(100)).execute().verifySuccess()

        // 1. User #1 puts item on sale.
        val firstSellOrderVersion = OrderVersion(
            maker = userSender1.from(),
            taker = null,
            make = Asset(Erc721AssetType(token721.address(), tokenId), EthUInt256.ONE),
            take = Asset(Erc20AssetType(token2.address()), EthUInt256.of(5)),
            type = OrderType.RARIBLE_V2,
            salt = EthUInt256.TEN,
            start = null,
            end = null,
            data = OrderRaribleV2DataV1(emptyList(), emptyList()),
            signature = null,
            createdAt = nowMillis(),
            makePriceUsd = null,
            takePriceUsd = null,
            makeUsd = null,
            takeUsd = null
        ).let {
            it.copy(
                signature = eip712Domain.hashToSign(Order.hash(it)).sign(privateKey1)
            )
        }

        val firstSellOrder = orderUpdateService.save(firstSellOrderVersion)

        // 2. User #2 buys the item.
        val preparedBuyBy2Tx = prepareTxService.prepareTransaction(
            firstSellOrder,
            PrepareOrderTxFormDto(userSender2.from(), EthUInt256.ONE.value, emptyList(), emptyList())
        )
        userSender2.sendTransaction(
            Transaction(
                exchange.address(),
                userSender2.from(),
                500000.toBigInteger(),
                BigInteger.ZERO,
                BigInteger.ZERO,
                preparedBuyBy2Tx.transaction.data,
                null
            )
        ).verifySuccess()

        // Check that the funds are moved as expected.
        Wait.waitAssert {
            assertEquals(userSender2.from(), token721.ownerOf(tokenId.value).call().awaitFirst())
            assertEquals(BigInteger.valueOf(5), token2.balanceOf(userSender1.from()).call().awaitFirst())
            assertEquals(BigInteger.valueOf(95), token2.balanceOf(userSender2.from()).call().awaitFirst())
        }

        // 3. User #2 puts item on sale.
        val secondSellOrderVersion = OrderVersion(
            maker = userSender2.from(),
            taker = null,
            make = Asset(Erc721AssetType(token721.address(), tokenId), EthUInt256.ONE),
            take = Asset(Erc20AssetType(token1.address()), EthUInt256.of(10)),
            type = OrderType.RARIBLE_V2,
            salt = EthUInt256.TEN,
            start = null,
            end = null,
            data = OrderRaribleV2DataV1(emptyList(), emptyList()),
            signature = null,
            createdAt = nowMillis(),
            makePriceUsd = null,
            takePriceUsd = null,
            makeUsd = null,
            takeUsd = null
        ).let {
            it.copy(
                signature = eip712Domain.hashToSign(Order.hash(it)).sign(privateKey2)
            )
        }

        val secondSellOrder = orderUpdateService.save(secondSellOrderVersion)

        // 4. User #1 buys the item back.
        val preparedBuyBy1Tx = prepareTxService.prepareTransaction(
            secondSellOrder,
            PrepareOrderTxFormDto(userSender1.from(), EthUInt256.ONE.value, emptyList(), emptyList())
        )
        userSender1.sendTransaction(
            Transaction(
                exchange.address(),
                userSender1.from(),
                500000.toBigInteger(),
                BigInteger.ZERO,
                BigInteger.ZERO,
                preparedBuyBy1Tx.transaction.data,
                null
            )
        ).verifySuccess()

        // Check that the funds are moved as expected.
        Wait.waitAssert {
            assertEquals(userSender1.from(), token721.ownerOf(tokenId.value).call().awaitFirst())

            assertEquals(BigInteger.valueOf(190), token1.balanceOf(userSender1.from()).call().awaitFirst())
            assertEquals(BigInteger.valueOf(10), token1.balanceOf(userSender2.from()).call().awaitFirst())

            assertEquals(BigInteger.valueOf(5), token2.balanceOf(userSender1.from()).call().awaitFirst())
            assertEquals(BigInteger.valueOf(95), token2.balanceOf(userSender2.from()).call().awaitFirst())
        }

        // 5. User #1 puts the item on sale again.
        val againSellOrderVersion = OrderVersion(
            maker = userSender1.from(),
            taker = null,
            make = Asset(Erc721AssetType(token721.address(), tokenId), EthUInt256.ONE),
            take = Asset(Erc20AssetType(token1.address()), EthUInt256.of(5)),
            type = OrderType.RARIBLE_V2,
            salt = EthUInt256.TEN,
            start = null,
            end = null,
            data = OrderRaribleV2DataV1(emptyList(), emptyList()),
            signature = null,
            createdAt = nowMillis(),
            makePriceUsd = null,
            takePriceUsd = null,
            makeUsd = null,
            takeUsd = null
        ).let {
            it.copy(
                signature = eip712Domain.hashToSign(Order.hash(it)).sign(privateKey1)
            )
        }

        val againSellOrder = orderUpdateService.save(againSellOrderVersion)

        // 6. User #2 buys the item again.
        val preparedBuyBy2AgainTx = prepareTxService.prepareTransaction(
            againSellOrder,
            PrepareOrderTxFormDto(userSender2.from(), EthUInt256.ONE.value, emptyList(), emptyList())
        )
        userSender2.sendTransaction(
            Transaction(
                exchange.address(),
                userSender2.from(),
                500000.toBigInteger(),
                BigInteger.ZERO,
                BigInteger.ZERO,
                preparedBuyBy2AgainTx.transaction.data,
                null
            )
        ).verifySuccess()

        Wait.waitAssert {
            assertEquals(userSender2.from(), token721.ownerOf(tokenId.value).call().awaitFirst())

            assertEquals(BigInteger.valueOf(195), token1.balanceOf(userSender1.from()).call().awaitFirst())
            assertEquals(BigInteger.valueOf(5), token1.balanceOf(userSender2.from()).call().awaitFirst())

            assertEquals(BigInteger.valueOf(5), token2.balanceOf(userSender1.from()).call().awaitFirst())
            assertEquals(BigInteger.valueOf(95), token2.balanceOf(userSender2.from()).call().awaitFirst())
        }

        Unit
    }
}
