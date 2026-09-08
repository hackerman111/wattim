package io.ronesec.android.domain.model

import java.time.Instant

sealed interface Decision {

    open class Allow(val reason: AllowReason = AllowReason.NOT_TARGET) : Decision {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Allow) return false
            // If checking against default Allow companion object, treat as matching any Allow
            if (this === Companion || other === Companion) return true
            return this.reason == other.reason
        }

        override fun hashCode(): Int = Allow::class.hashCode()

        override fun toString(): String = "Allow(reason=$reason)"

        companion object : Allow(AllowReason.NOT_TARGET)
    }

    data class Intervention(
        val config: InterventionConfig
    ) : Decision

    data class Block(
        val until: Instant?
    ) : Decision
}
