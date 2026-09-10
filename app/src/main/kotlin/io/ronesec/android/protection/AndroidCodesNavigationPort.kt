package io.ronesec.android.protection

import android.content.Context
import android.content.Intent
import io.ronesec.android.ui.MainActivity
import io.ronesec.domain.model.SessionId

class AndroidCodesNavigationPort(
    private val context: Context,
    private val onFailure: (SessionId, Int) -> Unit
) : CodesNavigationPort {
    override fun openWattim(sessionId: SessionId, cycle: Int) {
        try {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_CODES, true)
            })
        } catch (_: RuntimeException) {
            onFailure(sessionId, cycle)
        }
    }
}
