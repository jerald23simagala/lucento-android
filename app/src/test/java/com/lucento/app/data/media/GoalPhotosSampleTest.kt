package com.lucento.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalPhotosSampleTest {
    @Test
    fun smallPicturesAreDecodedAtFullSize() {
        assertEquals(1, GoalPhotos.sampleSizeFor(1000, 800))
    }

    @Test
    fun largePicturesAreReducedButKeepTheTargetEdge() {
        assertEquals(2, GoalPhotos.sampleSizeFor(4000, 3000))
        assertEquals(4, GoalPhotos.sampleSizeFor(8000, 6000))
    }
}
