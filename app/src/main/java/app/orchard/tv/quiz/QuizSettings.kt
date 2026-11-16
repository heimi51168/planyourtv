package app.orchard.tv.quiz

/**
 * The lock desktop's quiz-entry display switch, shared by the TV parent center
 * and the phone page.
 *
 * The quiz feature itself is always on in local mode. This switch only decides
 * whether the lock desktop shows the dedicated quiz tile first; entertainment
 * apps stay quiz triggers either way, and toggling it never touches a session.
 */
object QuizSettings {
    fun isLockEntryVisible(): Boolean = QuizSessionStore.lockEntryVisible()

    fun setLockEntryVisible(visible: Boolean) {
        QuizSessionStore.setLockEntryVisible(visible)
        QuizRuntime.refresh()
    }
}
