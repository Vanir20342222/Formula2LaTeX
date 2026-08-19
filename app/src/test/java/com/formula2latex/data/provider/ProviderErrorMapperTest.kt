package com.formula2latex.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderErrorMapperTest {
    @Test fun mapsCommonStatuses() {
        assertEquals(ProviderErrorKind.UNAUTHORIZED, ProviderErrorMapper.http(401, "bad").kind)
        assertEquals(ProviderErrorKind.RATE_LIMITED, ProviderErrorMapper.http(429, "quota").kind)
        assertEquals(ProviderErrorKind.IMAGE_TOO_LARGE, ProviderErrorMapper.http(413, "large").kind)
        assertEquals(ProviderErrorKind.SERVER_UNAVAILABLE, ProviderErrorMapper.http(503, "down").kind)
    }

    @Test fun exceptionStringDoesNotContainHeaders() {
        val error = ProviderErrorMapper.http(401, "invalid")
        assertFalse(error.toString().contains("Authorization", ignoreCase = true))
    }
}
