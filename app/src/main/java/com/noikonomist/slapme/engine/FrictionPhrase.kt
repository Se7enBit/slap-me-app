package com.noikonomist.slapme.engine

/** Checks the phrase typed in the friction override. Ignores case, extra spaces and a final period. */
object FrictionPhrase {

    fun matches(input: String, phrase: String = EngineConfig.Defaults.FRICTION_PHRASE): Boolean {
        val typed = normalize(input)
        return typed.isNotEmpty() && typed == normalize(phrase)
    }

    private fun normalize(text: String): String =
        text.trim().removeSuffix(".").trim().replace(WHITESPACE, " ").lowercase()

    private val WHITESPACE = Regex("\\s+")
}
