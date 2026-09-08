package io.ronesec.android.domain.protection

sealed interface AudioAcquireResult {
    data object Success : AudioAcquireResult
    data object FocusDenied : AudioAcquireResult
    data class Failed(val error: String) : AudioAcquireResult
}
