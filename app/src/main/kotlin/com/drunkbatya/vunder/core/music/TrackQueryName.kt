package com.drunkbatya.vunder.core.music

import com.drunkbatya.vunder.core.model.Track

val Track.searchQuery: String
    get() = "${queryName(artist)} ${queryName(title)}"

val Track.artistQuery: String
    get() = queryName(artist)

private fun queryName(value: String): String {
    var name = value
    val dot = name.indexOf('.')
    if (dot > 0 && name.substring(0, dot).all(Char::isDigit)) name = name.substring(dot + 1)
    name = name.replace('[', '(').replace(']', ')').trim()
    while (name.endsWith(")")) {
        val open = name.lastIndexOf('(')
        if (open < 0) break
        name = name.substring(0, open).trim()
    }
    return name.replace("\"", "").replace("\\", "").trim()
}
