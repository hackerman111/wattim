package io.ronesec.android.ui.designsystem

enum class ThemeId(val id: String, val displayName: String) {
    CYBER_TERMINAL("CYBER_TERMINAL", "Cyber Terminal"),
    NORD("NORD", "Nord"),
    CATPPUCCIN("CATPPUCCIN", "Catppuccin"),
    DRACULA("DRACULA", "Dracula"),
    GRUVBOX("GRUVBOX", "Gruvbox"),
    TOKYO_NIGHT("TOKYO_NIGHT", "Tokyo Night");

    companion object {
        val DEFAULT = NORD

        fun fromId(id: String?): ThemeId {
            if (id == null) return DEFAULT
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
