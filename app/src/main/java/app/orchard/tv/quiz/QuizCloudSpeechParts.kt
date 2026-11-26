package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizItem

/**
 * Maps the parent's speech choices to QB's per-part TTS contract.
 *
 * The server resolves the text from its own attempt set; [QuizTtsCache.Part.cacheText]
 * is only the local source used to key the downloaded MP3. Keeping these two
 * separate means the TV never uploads arbitrary text and can replay a cached
 * clip offline without asking QB for the URL again.
 */
internal object QuizCloudSpeechParts {

    fun question(item: QuizItem, settings: QuizSpeechSettings): List<QuizTtsCache.Part> {
        if (!settings.cloudActive) return emptyList()
        val parts = ArrayList<QuizTtsCache.Part>()
        if (settings.readPrompt) parts += QuizTtsCache.Part("stem", item.prompt)
        if (settings.readOptions) {
            item.options.forEachIndexed { index, option ->
                // QB resolves optionN in published order, while the TV may show a
                // shuffled order. Ask for the published slot that carries the
                // option visible at this display position.
                parts += QuizTtsCache.Part("option${publishedIndex(item, index)}", option)
            }
        }
        return parts
    }

    fun answer(item: QuizItem, settings: QuizSpeechSettings): List<QuizTtsCache.Part> {
        if (!settings.cloudActive || !settings.readAnswer) return emptyList()
        val answer = item.options.getOrNull(item.answerIndex) ?: return emptyList()
        val parts = ArrayList<QuizTtsCache.Part>(2)
        // The server's answer part answers with the published correct option
        // text, without a letter (the displayed letter changes after shuffle).
        // Key by that text so a reshuffle does not create duplicate audio.
        parts += QuizTtsCache.Part("answer", answer)
        val explanation = item.explanation.trim()
        if (explanation.isNotEmpty()) {
            parts += QuizTtsCache.Part("explanation", explanation)
        }
        return parts
    }

    /** Display position -> published position; empty/invalid maps as identity. */
    private fun publishedIndex(item: QuizItem, displayIndex: Int): Int {
        val order = item.optionOrder
        if (order.size != item.options.size) return displayIndex
        if (order.any { it !in item.options.indices }) return displayIndex
        if (order.distinct().size != order.size) return displayIndex
        return order[displayIndex]
    }
}
