package app.readbound.plugin

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginManifestTest {
    @Test fun parsesSelectionActionAndCapabilities() {
        val manifest = PluginManifest.parse("""{
          "id":"dev.example.dictionary","name":"Dictionary","version":"1.0.0","apiVersion":1,
          "permissions":["network"],"allowedDomains":["example.com"],
          "actions":[{"id":"lookup","title":"Lookup","context":"selection"}]
        }""")
        assertEquals(setOf("network"), manifest.permissions)
        assertEquals("lookup", manifest.actions.single().id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownCapability() {
        PluginManifest.parse("""{"id":"dev.bad.plugin","name":"Bad","version":"1","permissions":["filesystem"]}""")
    }
}
