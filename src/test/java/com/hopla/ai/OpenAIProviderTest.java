package com.hopla.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAIProviderTest {

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
    private OpenAIProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        HopLa.montoyaApi = montoyaApi;
        when(montoyaApi.logging()).thenReturn(logging);

        config = new LLMConfig();
        config.defaults = new LLMConfig.Defaults();
        config.defaults.timeout_sec = 10;

        providerConfig = new LLMConfig.Provider();
        providerConfig.chat_model = "gpt-4";
        providerConfig.chat_endpoint = "https://api.openai.com/v1/chat/completions";
        providerConfig.api_key = "test-key";

        provider = new OpenAIProvider(config, providerConfig);
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
    void testChatExcludesLastEmptyMessage() throws Exception {
        // Prepare chat history
        AIChats.Chat chat = new AIChats.Chat();
        chat.addMessage(new AIChats.Message(AIChats.MessageRole.USER, "Hello"));
        // AIChatPanel adds an empty assistant message before calling chat
        chat.addMessage(new AIChats.Message(AIChats.MessageRole.ASSISTANT, ""));

        // Mock response
        ResponseBody body = ResponseBody.create("{}", MediaType.parse("application/json"));
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://api.openai.com/v1/chat/completions").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();

        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        provider.chat(chat, new AIProvider.StreamingCallback() {
            @Override public void onData(String chunk) {}
            @Override public void onDone() {}
            @Override public void onError(String error) {}
        });

        // Verify Request
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(client).newCall(requestCaptor.capture());
        Request capturedRequest = requestCaptor.getValue();

        Buffer buffer = new Buffer();
        capturedRequest.body().writeTo(buffer);
        String bodyString = buffer.readUtf8();

        JsonObject json = JsonParser.parseString(bodyString).getAsJsonObject();
        JsonArray messages = json.getAsJsonArray("messages");

        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Hello", messages.get(0).getAsJsonObject().get("content").getAsString());
    }
}
