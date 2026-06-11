package com.openrouter.gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.config.AppProperties;
import com.openrouter.gateway.config.ModelConfigService;
import com.openrouter.gateway.orchestrator.OrchestratorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouterClientQueryModelTest {

    @Mock private HttpClient httpClient;
    @Mock private ModelConfigService modelConfigService;

    private OpenRouterClient client;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getOpenrouter().setProxyUrl("http://localhost:8081");
        client = new OpenRouterClient(httpClient, new ObjectMapper(), props, modelConfigService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryModel_200Response_returnsSuccess() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn(
                "{\"choices\":[{\"message\":{\"content\":\"Paris\"}}]}");
        when(httpClient.send(any(HttpRequest.class),
                any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        OrchestratorResult result = client.queryModel(
                "mistralai/mistral-7b-instruct:free", "Mistral 7B",
                "Capital of France?", "sk-or-test");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.content()).isEqualTo("Paris");
        assertThat(result.modelId()).isEqualTo("mistralai/mistral-7b-instruct:free");
        assertThat(result.name()).isEqualTo("Mistral 7B");
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryModel_429Response_returnsTIMEOUT() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(429);
        when(httpClient.send(any(HttpRequest.class),
                any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        OrchestratorResult result = client.queryModel(
                "mistralai/mistral-7b-instruct:free", "Mistral 7B", "Hello", "sk-or-test");

        assertThat(result.status()).isEqualTo("TIMEOUT");
        assertThat(result.content()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryModel_500Response_returnsERROR() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class),
                any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(mockResp);

        OrchestratorResult result = client.queryModel(
                "mistralai/mistral-7b-instruct:free", "Mistral 7B", "Hello", "sk-or-test");

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.content()).isNull();
    }
}
