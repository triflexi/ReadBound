package app.readbound.importer

internal object BookFormatDetector {
    fun detect(displayName: String, mimeType: String?, prefix: ByteArray): String? {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension in setOf("epub", "fb2", "txt")) return extension

        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (mime == "application/epub+zip") return "epub"
        if (mime in setOf("application/x-fictionbook+xml", "application/fb2+xml")) return "fb2"

        if (prefix.size >= 4 && prefix[0] == 'P'.code.toByte() && prefix[1] == 'K'.code.toByte()) return "epub"
        val header = prefix.toString(Charsets.UTF_8).lowercase()
        if (Regex("<(?:[a-z0-9_-]+:)?fictionbook(?:\\s|>)").containsMatchIn(header)) return "fb2"
        if (mime in setOf("text/plain")) return "txt"
        return null
    }
}
