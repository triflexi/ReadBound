package app.readbound.importer

import org.json.JSONArray
import org.json.JSONObject

data class PublicationChapter(val title: String, val href: String)

data class PublicationManifest(val chapters: List<PublicationChapter>) {
    fun toJson(): String = JSONArray().apply {
        chapters.forEach { put(JSONObject().put("title", it.title).put("href", it.href)) }
    }.toString()

    companion object {
        fun fromJson(json: String): PublicationManifest {
            val array = JSONArray(json)
            return PublicationManifest(List(array.length()) { index ->
                val item = array.getJSONObject(index)
                PublicationChapter(item.optString("title", "Chapter ${index + 1}"), item.getString("href"))
            })
        }
    }
}
