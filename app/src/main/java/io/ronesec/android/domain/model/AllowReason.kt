package io.ronesec.android.domain.model

enum class AllowReason {
    NOT_TARGET,
    TARGET_DISABLED,
    GLOBAL_PAUSE,
    ACTIVE_SESSION_PERMIT,
    ACTIVE_TIMED_PERMIT,
    QUICK_RETURN
}
