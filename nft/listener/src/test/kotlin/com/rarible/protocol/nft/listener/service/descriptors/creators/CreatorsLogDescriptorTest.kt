package com.rarible.protocol.nft.listener.service.descriptors.creators

import com.rarible.core.test.wait.Wait
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.contracts.erc721.rarible.ERC721Rarible
import com.rarible.protocol.nft.core.model.*
import com.rarible.protocol.nft.listener.integration.AbstractIntegrationTest
import com.rarible.protocol.nft.listener.integration.IntegrationTest
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import reactor.core.publisher.Mono
import scala.Tuple2
import scala.Tuple5
import scalether.domain.Address
import scalether.domain.AddressFactory
import scalether.transaction.MonoGasPriceProvider
import scalether.transaction.MonoSigningTransactionSender
import scalether.transaction.MonoSimpleNonceProvider
import scalether.util.Hex
import java.math.BigInteger

@IntegrationTest
class CreatorsLogDescriptorTest : AbstractIntegrationTest() {

    @Test
    fun `should handle mint event`() = runBlocking {
        val privateKey = Numeric.toBigInt(RandomUtils.nextBytes(32))
        val address = Address.apply(Keys.getAddressFromPrivateKey(privateKey))

        val userSender = MonoSigningTransactionSender(
            ethereum,
            MonoSimpleNonceProvider(ethereum),
            privateKey,
            BigInteger.valueOf(8000000),
            MonoGasPriceProvider { Mono.just(BigInteger.ZERO) }
        )

        val token = ERC721Rarible.deployAndWait(userSender, poller).awaitFirst()
        token.__ERC721Rarible_init("Test", "TestSymbol", "BASE", "URI").execute().verifySuccess()

        val tokenId = EthUInt256.of("0x${Hex.to(address.bytes())}00000000000000000000006B")

        val mintData = Tuple5(
            tokenId.value,
            "TestUri",
            arrayOf(
                Tuple2(
                    address,
                    BigInteger.valueOf(10000)
                )
            ),
            emptyArray<Tuple2<Address, BigInteger>>(),
            arrayOf(ByteArray(0))
        )

        val transferTo = AddressFactory.create()
        val receipt = token.mintAndTransfer(mintData, transferTo)
            .withSender(userSender)
            .execute()
            .verifySuccess()
        println(receipt.logs())

        Wait.waitAssert {
            assertThat(tokenRepository.count().awaitFirst()).isEqualTo(1)
            val savedToken = tokenRepository.findById(token.address()).awaitFirst()

            assertEquals(savedToken.status, ContractStatus.CONFIRMED)
            assertEquals(savedToken.name, "Test")
            assertEquals(savedToken.owner, address)
            assertEquals(savedToken.symbol, "TestSymbol")
        }
        Wait.waitAssert {
            val creators = nftItemHistoryRepository
                .findItemsHistory(token.address(), tokenId = null)
                .filter { it.log.topic in ItemType.CREATORS.topic }
                .collectList().awaitFirst()

            assertThat(creators).hasSize(1)

            with(creators.first().item as ItemCreators) {
                assertThat(this.creators).isEqualTo(listOf(Part(address, 10000)))
                assertThat(this.token).isEqualTo(token.address())
                assertThat(this.tokenId).isEqualTo(tokenId)
            }
        }
        Wait.waitAssert {
            val itemId = ItemId(token.address(), tokenId)
            val item = itemRepository.findById(itemId).awaitFirstOrNull()

            assertThat(item).isNotNull

            with(item) {
                assertThat(this?.creators).isEqualTo(listOf(Part(address, 10000)))
            }
        }
        Wait.waitAssert {
            val ownerships = ownershipRepository.search(Query(Criteria.where(Ownership::token.name).isEqualTo(token.address())))

            assertThat(ownerships).hasSize(1)
            assertThat(ownerships.single().owner).isEqualTo(transferTo)
            assertThat(ownerships.single().creators).isEqualTo(listOf(Part(address, 10000)))
        }
    }
}
