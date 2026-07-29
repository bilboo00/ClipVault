package com.clipvault.manager.ui.nav

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Search : Route("search")
    data object Stats : Route("stats")
    data object Settings : Route("settings")
    data object Snippets : Route("snippets")
    data object Tags : Route("tags")
    data object Collections : Route("collections")
    data object Detail : Route("detail/{clipId}") {
        fun build(clipId: Long) = "detail/$clipId"
        const val ARG = "clipId"
    }
}