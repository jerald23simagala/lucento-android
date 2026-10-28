package com.lucento.app.core.crash

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportOptOutTest {

    @Test fun optingOutPersistsBothStoresBeforeDisablingCollection() = runBlocking {
        val log = mutableListOf<String>()
        CrashReportOptOut.apply(
            enabled = false,
            persistDataStore = { log += "datastore:$it" },
            persistMirror = { log += "mirror:$it" },
            setCollectionEnabled = { log += "collection:$it" },
            deleteUnsentReports = { log += "deleteUnsent" },
        )
        assertEquals(
            listOf("datastore:false", "mirror:false", "collection:false", "deleteUnsent"),
            log,
        )
    }

    @Test fun optingInPersistsBothStoresBeforeEnablingAndDoesNotDeleteUnsent() = runBlocking {
        val log = mutableListOf<String>()
        CrashReportOptOut.apply(
            enabled = true,
            persistDataStore = { log += "datastore:$it" },
            persistMirror = { log += "mirror:$it" },
            setCollectionEnabled = { log += "collection:$it" },
            deleteUnsentReports = { log += "deleteUnsent" },
        )
        assertEquals(listOf("datastore:true", "mirror:true", "collection:true"), log)
    }
}
