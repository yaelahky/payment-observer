package com.fgteam.paymentobserver.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserverSetupTest {
    @Test
    fun `manufacturer guide groups aggressive battery managers`() {
        assertEquals("Xiaomi / Redmi / POCO", ObserverSetup.guideForManufacturer("Xiaomi")?.name)
        assertEquals("OPPO / realme / OnePlus", ObserverSetup.guideForManufacturer("realme")?.name)
        assertEquals("vivo / iQOO", ObserverSetup.guideForManufacturer("VIVO")?.name)
        assertEquals("Huawei / Honor", ObserverSetup.guideForManufacturer("HONOR")?.name)
        assertEquals("Samsung", ObserverSetup.guideForManufacturer("samsung")?.name)
    }

    @Test
    fun `unknown manufacturer uses standard Android setup only`() {
        assertNull(ObserverSetup.guideForManufacturer("generic"))
    }
}
