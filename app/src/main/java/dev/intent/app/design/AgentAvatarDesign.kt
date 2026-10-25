package dev.intent.app.design

import androidx.annotation.DrawableRes
import dev.intent.app.R
import kotlin.math.abs

/** Catalog order and fallback arithmetic match iOS AgentAvatarDesign and desktop JavaScript. */
enum class AgentAvatarDesign(val wireName: String, @DrawableRes val drawable: Int) {
    Coordinator("coordinator", R.drawable.avatar_coordinator),
    Implementor("implementor", R.drawable.avatar_implementor),
    Verifier("verifier", R.drawable.avatar_verifier),
    PrReviewer("pr-reviewer", R.drawable.avatar_pr_reviewer),
    UiDesigner("ui-designer", R.drawable.avatar_ui_designer),
    ChiefOfStaff("chief-of-staff", R.drawable.avatar_chief_of_staff),
    Ralph("ralph", R.drawable.avatar_ralph),
    FallbackGlasses("fallback-glasses", R.drawable.avatar_fallback_glasses),
    FallbackCrown("fallback-crown", R.drawable.avatar_fallback_crown),
    FallbackCrownInverted("fallback-crown-inverted", R.drawable.avatar_fallback_crown_inverted),
    FallbackCrownMouth("fallback-crown-mouth", R.drawable.avatar_fallback_crown_mouth),
    FallbackCode("fallback-code", R.drawable.avatar_fallback_code),
    FallbackDots("fallback-dots", R.drawable.avatar_fallback_dots);

    companion object {
        private val fallbacks = entries.filter { it.wireName.startsWith("fallback-") }
        private val builtins = entries.filterNot { it in fallbacks }.associateBy { it.wireName }

        fun resolve(
            agentId: String,
            specialist: String? = null,
            icon: String? = null
        ): AgentAvatarDesign = builtins[icon] ?: builtins[
            when (specialist) {
                "spec-writer" -> "coordinator"
                "developer" -> "verifier"
                else -> specialist
            }
        ] ?: fallback(agentId)

        @Suppress("MagicNumber")
        private fun fallback(agentId: String): AgentAvatarDesign {
            // Kotlin String.hashCode uses UTF-16 and signed Int overflow, as JS does here.
            // Double multiplication deliberately reproduces JavaScript's lost low bits.
            val product = abs(agentId.hashCode().toLong()).toDouble() * 1_103_515_245.0 + 12_345.0
            val seed = (product % 4_294_967_296.0).toLong() and 0x7fffffffL
            return fallbacks[(seed / 2_147_483_648.0 * fallbacks.size).toInt()]
        }
    }
}
