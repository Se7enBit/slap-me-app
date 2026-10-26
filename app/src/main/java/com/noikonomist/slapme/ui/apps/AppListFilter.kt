package com.noikonomist.slapme.ui.apps

data class AppEntry(val packageName: String, val label: String)

object AppListFilter {
    /** One entry per app, without Slap Me, matching [query]; [selected] apps first, then by name. */
    fun apply(apps: List<AppEntry>, selected: Set<String>, query: String, self: String): List<AppEntry> {
        val q = query.trim()
        return apps
            .distinctBy { it.packageName }
            .filter { it.packageName != self }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedWith(
                compareByDescending<AppEntry> { it.packageName in selected }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }
}
