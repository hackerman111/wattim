package io.ronesec.android.domain.model

import java.time.Instant

sealed interface Decision {

    data object Allow : Decision

    data class Intervention(
        val config: InterventionConfig
    ) : Decision

    data class Block(
        val until: Instant?
    ) : Decision
}
