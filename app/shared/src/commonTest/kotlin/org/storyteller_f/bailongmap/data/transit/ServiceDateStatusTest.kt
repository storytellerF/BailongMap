package org.storyteller_f.bailongmap.data.transit

import kotlin.test.*

class ServiceDateStatusTest {
    private val info = TransitPackageInfo("Example", "Synthetic", "20261101", "20261130", 2, 1)

    @Test fun validityIncludesBothServiceDateBoundaries() {
        assertEquals(ServiceDateStatus.VALID, info.serviceDateStatus("20261101"))
        assertEquals(ServiceDateStatus.VALID, info.serviceDateStatus("20261130"))
    }

    @Test fun datesOutsideCoverageAreNotShownAsReady() {
        assertEquals(ServiceDateStatus.UPCOMING, info.serviceDateStatus("20261031"))
        assertEquals(ServiceDateStatus.EXPIRED, info.serviceDateStatus("20261201"))
    }
}
