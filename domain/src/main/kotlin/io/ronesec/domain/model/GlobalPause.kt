package io.ronesec.domain.model

import java.time.Instant

sealed interface GlobalPause {
    fun isActive(now: Instant): Boolean

    data object None : GlobalPause {
        override fun isActive(now: Instant): Boolean = false
    }

    data class Until(val until: Instant) : GlobalPause {
        override fun isActive(now: Instant): Boolean = now.isBefore(until)
    }

    data object Indefinite : GlobalPause {
        override fun isActive(now: Instant): Boolean = true
    }
}
