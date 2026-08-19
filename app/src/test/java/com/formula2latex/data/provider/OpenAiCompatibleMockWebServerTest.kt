package com.formula2latex.data.provider

import com.formula2latex.domain.model.Capability
import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.domain.model.ProviderConfig
import com.formula2latex.domain.model.ProviderKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleMockWebServerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @After fun tearDown() = server.shutdown()

    @Test fun openRouterDiscoversCapabilitiesAndRetriesRejectedSchemaOnce() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"org/vision:free","name":"Vision","architecture":{"input_modalities":["text","image"]},"supported_parameters":["structured_outputs"]}]}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"response_format is unsupported"}}"""))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"latex\":\"x^2\",\"confidence\":0.9,\"alternatives\":[],\"warnings\":[]}"}}]}"""))
        val provider = OpenRouterFormulaProvider(client)
        val config = ProviderConfig(ProviderKind.OPEN_ROUTER, "fake-key", server.url("/").toString().trimEnd('/'))

        val model = provider.listModels(config).getOrThrow().single()
        assertEquals("org/vision:free", model.id)
        assertEquals(Capability.SUPPORTED, model.imageInput)
        assertEquals(Capability.SUPPORTED, model.structuredOutput)
        assertEquals("x^2", provider.convert(config, model.id, FormulaInput.Description("x squared")).getOrThrow().latex)

        val discovery = server.takeRequest()
        assertEquals("Bearer fake-key", discovery.getHeader("Authorization"))
        val structured = server.takeRequest().body.readUtf8()
        val fallback = server.takeRequest().body.readUtf8()
        assertTrue(structured.contains("response_format"))
        assertTrue(structured.contains("require_parameters"))
        assertFalse(fallback.contains("response_format"))
    }

    @Test fun customEndpointAllowsEmptyKeyAndAcceptsRawLatexFallback() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"$$\\frac{a}{b}$$"}}]}"""))
        val provider = CustomFormulaProvider(client)
        val config = ProviderConfig(ProviderKind.CUSTOM, "", server.url("/").toString().trimEnd('/'))

        val result = provider.convert(config, "local/model", FormulaInput.Description("a over b")).getOrThrow()
        assertEquals("\\frac{a}{b}", result.latex)
        assertTrue(result.warnings.single().contains("schema"))
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }
}
