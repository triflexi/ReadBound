package app.readbound.data

data class ReaderTarget(val chapterIndex: Int, val chapterProgress: Double)

fun globalProgress(chapterIndex: Int, chapterProgress: Double, chapterCount: Int): Double {
    if (chapterCount <= 0) return 0.0
    return ((chapterIndex.coerceAtLeast(0) + chapterProgress.coerceIn(0.0, 1.0)) / chapterCount).coerceIn(0.0, 1.0)
}

fun targetForProgress(progress: Double, chapterCount: Int): ReaderTarget {
    if (chapterCount <= 0) return ReaderTarget(0, 0.0)
    val absolute = progress.coerceIn(0.0, 1.0) * chapterCount
    val chapter = absolute.toInt().coerceIn(0, chapterCount - 1)
    return ReaderTarget(chapter, (absolute - chapter).coerceIn(0.0, 1.0))
}
