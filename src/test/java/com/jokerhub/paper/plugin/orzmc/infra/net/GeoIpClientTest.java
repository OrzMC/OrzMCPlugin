package com.jokerhub.paper.plugin.orzmc.infra.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GeoIpClientTest {

    private GeoIpClient client;

    @BeforeEach
    void setUp() {
        client = new GeoIpClient();
    }

    @Test
    void constructor_createsInstance() {
        assertNotNull(client);
    }

    @Test
    void lookup_returnsCompletableFuture() {
        CompletableFuture<GeoIpClient.GeoIpResult> future = client.lookup("8.8.8.8");
        assertNotNull(future);
    }

    @Test
    void lookup_constructsCorrectUrlAndParsesResult() throws Exception {
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            // Prepare mock response
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            when(httpResponse.body()).thenReturn("{\"country_code\":\"CN\"}");

            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(httpResponse);
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            // Execute
            GeoIpClient.GeoIpResult result = client.lookup("1.2.3.4").join();

            // Verify
            assertEquals("CN", result.countryCode());
            assertEquals("{\"country_code\":\"CN\"}", result.rawJson());

            asyncHttp.verify(() -> AsyncHttp.get(
                    eq("https://get.geojs.io/v1/ip/geo/1.2.3.4.json"),
                    eq(Map.of()),
                    eq(Duration.ofSeconds(3)),
                    eq(Duration.ofSeconds(3)),
                    eq(2)));
        }
    }

    @Test
    void lookup_parsesEmptyCountryCode() throws Exception {
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            // Response without country_code
            when(httpResponse.body()).thenReturn("{\"ip\":\"1.2.3.4\"}");

            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(httpResponse);
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            GeoIpClient.GeoIpResult result = client.lookup("1.2.3.4").join();

            assertEquals("", result.countryCode());
        }
    }

    @Test
    void lookup_handlesHttpError() throws Exception {
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            CompletableFuture<HttpResponse<String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Connection refused"));

            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(failedFuture);

            assertThrows(Exception.class, () -> client.lookup("1.2.3.4").join());
        }
    }

    @Test
    void lookup_handlesInvalidJson() throws Exception {
        try (MockedStatic<AsyncHttp> asyncHttp = mockStatic(AsyncHttp.class)) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            when(httpResponse.body()).thenReturn("invalid json");

            CompletableFuture<HttpResponse<String>> future = CompletableFuture.completedFuture(httpResponse);
            asyncHttp
                    .when(() ->
                            AsyncHttp.get(anyString(), anyMap(), any(Duration.class), any(Duration.class), anyInt()))
                    .thenReturn(future);

            assertThrows(Exception.class, () -> client.lookup("1.2.3.4").join());
        }
    }
}
