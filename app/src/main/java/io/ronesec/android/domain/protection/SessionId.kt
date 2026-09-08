package io.ronesec.android.domain.protection

@JvmInline
value class SessionId(val value: Long) {
    fun next(): SessionId = SessionId(value + 1L)

    companion object {
        val NONE = SessionId(0L)
        val INITIAL = SessionId(1L)
    }
}
