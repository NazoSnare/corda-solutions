package com.r3.businessnetworks.testutilities

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

/**
 * @param numberOfBusinessNetworks number of BNOs to create. BNOs name is represented with O=BNO_{index of bno},L=New York,C=US, starting from 0
 *      i.e. O=BNO_0,L=New York,C=US, O=BNO_1,L=New York,C=US .... O=BNO_N,L=New York,C=US
 * @param numberOfParticipants *total* number of participants to create. Participants don't have business network membership initially
 * @param participantRespondingFlows responding flows to register for participant nodes
 * @param bnoRespondingFlows responding flows to register for BNO nodes
 */
abstract class AbstractBusinessNetworksFlowTest(private val numberOfBusinessNetworks : Int,
                                                private val numberOfParticipants : Int,
                                                private val cordappPackages : List<String>,
                                                private val notaryName : String = "O=Notary,L=London,C=GB",
                                                private val participantRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf(),
                                                private val bnoRespondingFlows : List<Class<out FlowLogic<Any>>> = listOf()) {
    lateinit var bnoNodes : List<StartedMockNode>
    lateinit var participantsNodes : List<StartedMockNode>
    lateinit var mockNetwork : MockNetwork

    @Before
    open fun setup() {
        mockNetwork = MockNetwork(
                // legacy API is used on purpose as otherwise flows defined in tests are not picked up by the framework
                cordappPackages = cordappPackages,
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name.parse(notaryName)))
        )
        bnoNodes = (1..numberOfBusinessNetworks).mapIndexed { indexOfBN, _ ->
            val bnoName =  CordaX500Name.parse("O=BNO_$indexOfBN,L=New York,C=US")
            val bnoNode = createNode(bnoName)
            bnoRespondingFlows.forEach { bnoNode.registerInitiatedFlow(it) }
            bnoNode
        }

        participantsNodes = (1..numberOfParticipants).map { indexOfParticipant ->
            val node = createNode(CordaX500Name.parse("O=Participant $indexOfParticipant,L=London,C=GB"))
            participantRespondingFlows.forEach { node.registerInitiatedFlow(it) }
            node
        }

        mockNetwork.runNetwork()
    }

    private fun createNode(name : CordaX500Name) =
            mockNetwork.createNode(MockNodeParameters(legalName = name))

    @After
    open fun tearDown() {
        mockNetwork.stopNodes()
    }

    protected fun <T> runFlowAndReturn(node: StartedMockNode, flow: FlowLogic<T>): T {
        val future = node.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }
}

fun StartedMockNode.identity() = info.legalIdentities.single()
fun List<StartedMockNode>.identities() = map { it.identity() }