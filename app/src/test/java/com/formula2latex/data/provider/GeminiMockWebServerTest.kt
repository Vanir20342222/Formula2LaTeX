package com.formula2latex.data.provider

import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.domain.model.ProviderConfig
import com.formula2latex.domain.model.ProviderKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiMockWebServerTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: GeminiFormulaProvider

    @Before fun setUp() {
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        provider = GeminiFormulaProvider(OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun discoversAndConvertsWithoutLeakingKeyIntoUrl() = runTest {
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/gemini-test","baseModelId":"gemini-test","displayName":"Test","supportedGenerationMethods":["generateContent"]}]}"""))
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{\"latex\":\"x^2\",\"confidence\":1,\"alternatives\":[],\"warnings\":[]}" }]}}]}"""))
        val config = ProviderConfig(ProviderKind.GEMINI, "test-secret", server.url("/").toString().trimEnd('/'))
        val models = provider.listModels(config).getOrThrow()
        assertEquals("gemini-test", models.single().id)
        val result = provider.convert(config, models.single().id, FormulaInput.Description("x squared")).getOrThrow()
        assertEquals("x^2", result.latex)
        val discovery = server.takeRequest()
        assertEquals("test-secret", discovery.getHeader("x-goog-api-key"))
        assertFalse(discovery.path!!.contains("test-secret"))
        val conversion = server.takeRequest()
        assertTrue(conversion.body.readUtf8().contains("responseJsonSchema"))
    }

    private fun assertFalse(value: Boolean) = org.junit.Assert.assertFalse(value)
}
