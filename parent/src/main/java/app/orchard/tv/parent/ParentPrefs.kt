package app.orchard.tv.parent

import android.content.Context

/**
 * The paired TV address. It is the only thing the shell remembers: the admin
 * session and its expiry stay in the page's own localStorage, exactly as they
 * would in a browser.
 */
internal class ParentPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var quizUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_URL) else putString(KEY_URL, value)
            }.apply()
        }

    fun clear() {
        prefs.edit().remove(KEY_URL).apply()
    }

    private companion object {
        const val NAME = "parent_shell"
        const val KEY_URL = "quiz_url"
    }
}
