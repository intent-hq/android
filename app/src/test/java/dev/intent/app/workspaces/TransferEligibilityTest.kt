package dev.intent.app.workspaces

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferEligibilityTest {
    @Test fun legacyOwnerRequiresExplicitAdministratorAndIdentity() {
        assertTrue(owner("""{"id":"person","isAdministrator":true}"""))
        listOf(
            """{"id":"person","isAdministrator":false}""",
            """{"id":"person","isAdministrator":"true"}""",
            """{"id":"person"}""",
            """{"isAdministrator":true}""",
            """{"id":12,"isAdministrator":true}""",
            """{"id":"person","isAdministrator":true,"hostRole":"member"}"""
        ).forEach { assertFalse(owner(it)) }
    }

    @Test fun membershipHostsRequireConsistentOwnerAndRevision() {
        assertTrue(
            owner(
                """{"id":"person","isAdministrator":true,"hostRole":"owner","hostMembershipRevision":4}""",
                """{"hostMembership":1}"""
            )
        )
        listOf(
            """{"id":"person","isAdministrator":true}""",
            """{"id":"person","isAdministrator":true,"hostRole":"owner"}""",
            """{"id":"person","isAdministrator":false,"hostRole":"owner","hostMembershipRevision":4}""",
            """{"id":"person","isAdministrator":true,"hostRole":"owner","hostMembershipRevision":-1}""",
            """{"id":"person","isAdministrator":true,"hostRole":"owner","hostMembershipRevision":"4"}"""
        ).forEach { assertFalse(owner(it, """{"hostMembership":1}""")) }
    }

    private fun owner(principal: String, capabilities: String = "{}") = transferPrincipalIsOwner(
        Json.parseToJsonElement(principal) as JsonObject,
        Json.parseToJsonElement(capabilities) as JsonObject
    )
}
