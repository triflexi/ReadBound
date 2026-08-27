package app.readbound.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionTest {
    @Test fun globalProgressCombinesChapterAndLocalProgress() {
        assertEquals(0.375, globalProgress(1, 0.5, 4), 0.000001)
    }

    @Test fun progressTargetClampsEndToLastChapter() {
        assertEquals(ReaderTarget(3, 1.0), targetForProgress(1.0, 4))
    }

    @Test fun emptyPublicationIsSafe() {
        assertEquals(0.0, globalProgress(4, 0.8, 0), 0.0)
        assertEquals(ReaderTarget(0, 0.0), targetForProgress(0.8, 0))
    }
}
