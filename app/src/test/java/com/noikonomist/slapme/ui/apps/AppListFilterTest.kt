package com.noikonomist.slapme.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private val apps = listOf(
        AppEntry("com.zhiliaoapp.musically", "TikTok"),
        AppEntry("com.google.android.youtube", "YouTube"),
        AppEntry("com.android.chrome", "Chrome"),
        AppEntry("com.noikonomist.slapme", "Slap Me"),
        AppEntry("com.google.android.youtube", "YouTube"), // second launcher activity
        AppEntry("com.instagram.android", "instagram"),
    )

    private fun filter(selected: Set<String> = emptySet(), query: String = "") =
        AppListFilter.apply(apps, selected, query, self = "com.noikonomist.slapme").map { it.label }

    @Test
    fun sortedByNameWithoutSelfOrDuplicates() {
        assertEquals(listOf("Chrome", "instagram", "TikTok", "YouTube"), filter())
    }

    @Test
    fun selectedAppsComeFirst() {
        assertEquals(listOf("YouTube", "Chrome", "instagram", "TikTok"), filter(selected = setOf("com.google.android.youtube")))
    }

    @Test
    fun searchMatchesNameOrPackageIgnoringCase() {
        assertEquals(listOf("YouTube"), filter(query = "tube"))
        assertEquals(listOf("TikTok"), filter(query = "musically"))
        assertEquals(listOf("instagram"), filter(query = "INSTA"))
        assertEquals(emptyList<String>(), filter(query = "slap"))
    }
}
