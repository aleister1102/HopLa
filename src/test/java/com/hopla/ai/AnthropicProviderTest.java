package com.hopla.ai;

import com.hopla.HopLa;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnthropicProviderTest {

    @Mock
    private MontoyaApi montoyaApi;
    @Mock
    private Logging logging;
    @Mock
    private OkHttpClient client;
    @Mock
    private Call call;

    private LLMConfig config;
    private LLMConfig.Provider providerConfig;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        HopLa.montoyaApi = montoyaApi;
        when(montoyaApi.logging()).thenReturn(logging);

        config = new LLMConfig();
        config.defaults = new LLMConfig.Defaults();
        config.defaults.timeout_sec = 10;

        providerConfig = new LLMConfig.Provider();
        providerConfig.quick_action_model = "claude-3-5-sonnet-20241022";
        providerConfig.quick_action_endpoint = "https://api.anthropic.com/v1/messages";
        providerConfig.chat_model = "claude-3-5-sonnet-20241022";
        providerConfig.chat_endpoint = "https://api.anthropic.com/v1/messages";
        providerConfig.api_key = "test-key";

        provider = new AnthropicProvider(config, providerConfig);
        // Inject mocked client
        try {
            java.lang.reflect.Field clientField = AIProvider.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(provider, client);
        } catch (Exception e) {
            fail("Failed to inject mock client");
        }
    }

    @Test
    void testInstructStreaming() throws Exception {
        // Prepare mock response
        String sseResponse = "event: message_start\n" +
                "data: {\"type\": \"message_start\", \"message\": {\"id\": \"msg_1\", \"type\": \"message\", \"role\": \"assistant\", \"model\": \"claude-3-5-sonnet-20241022\", \"content\": [], \"stop_reason\": null, \"stop_sequence\": null, \"usage\": {\"input_tokens\": 10, \"output_tokens\": 1}}}\n" +
                "\n" +
                "event: content_block_start\n" +
                "data: {\"type\": \"content_block_start\", \"index\": 0, \"content_block\": {\"type\": \"text\", \"text\": \"\"}}\n" +
                "\n" +
                "event: content_block_delta\n" +
                "data: {\"type\": \"content_block_delta\", \"index\": 0, \"delta\": {\"type\": \"text_delta\", \"text\": \"Hello\"}}\n" +
                "\n" +
                "event: content_block_delta\n" +
                "data: {\"type\": \"content_block_delta\", \"index\": 0, \"delta\": {\"type\": \"text_delta\", \"text\": \" World\"}}\n" +
                "\n" +
                "event: message_delta\n" +
                "data: {\"type\": \"message_delta\", \"delta\": {\"stop_reason\": \"end_turn\", \"stop_sequence\": null}, \"usage\": {\"output_tokens\": 3}}\n" +
                "\n" +
                "event: message_stop\n" +
                "data: {\"type\": \"message_stop\"}\n" +
                "\n";

        ResponseBody body = ResponseBody.create(sseResponse, MediaType.parse("text/event-stream"));
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://api.anthropic.com/v1/messages").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();

        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();

        provider.instruct("Hi", new AIProvider.StreamingCallback() {
            @Override
            public void onData(String chunk) {
                result.append(chunk);
            }

            @Override
            public void onDone() {
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                fail("onError called: " + error);
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("Hello World", result.toString());
        
        // Verify Request
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(client).newCall(requestCaptor.capture());
        Request capturedRequest = requestCaptor.getValue();
        
        assertEquals("https://api.anthropic.com/v1/messages", capturedRequest.url().toString());
        assertEquals("test-key", capturedRequest.header("x-api-key"));
        assertEquals("2023-06-01", capturedRequest.header("anthropic-version"));
        
        Buffer buffer = new Buffer();
        capturedRequest.body().writeTo(buffer);
        String bodyString = buffer.readUtf8();
        assertTrue(bodyString.contains("\"model\":\"claude-3-5-sonnet-20241022\""));
        assertTrue(bodyString.contains("\"content\":\"Hi\""));
    }
}
