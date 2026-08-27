package app.readbound.plugin

import org.json.JSONArray
import org.json.JSONObject

data class PluginAction(
    val pluginId: String,
    val id: String,
    val title: String,
    val context: String,
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val entrypoint: String,
    val permissions: Set<String>,
    val allowedDomains: Set<String>,
    val actions: List<PluginAction>,
) {
    companion object {
        fun parse(json: String): PluginManifest {
            val value = JSONObject(json)
            val id = value.getString("id")
            require(id.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{2,100}"))) { "Invalid plugin id" }
            val permissions = value.optJSONArray("permissions").strings().toSet()
            require(permissions.all { it in setOf("network", "anki.write") }) { "Unknown plugin permission" }
            val domains = value.optJSONArray("allowedDomains").strings().toSet()
            val actions = value.optJSONArray("actions")?.let { array ->
                List(array.length()) { index ->
                    val action = array.getJSONObject(index)
                    require(action.getString("id").matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{1,60}"))) { "Invalid action id" }
                    PluginAction(id, action.getString("id"), action.getString("title"), action.optString("context", "selection"))
                }
            }.orEmpty()
            return PluginManifest(
                id,
                value.getString("name"),
                value.getString("version"),
                value.optInt("apiVersion", 1),
                value.optString("entrypoint", "main.js"),
                permissions,
                domains,
                actions,
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
