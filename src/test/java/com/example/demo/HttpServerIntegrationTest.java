package com.example.demo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerIntegrationTest {

    private static int testPort;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static Thread serverThread;

    @BeforeAll
    static void bootServer() throws InterruptedException {
        testPort = findAvailablePort();

        HttpServer.staticfiles("/webroot");

        HttpServer.get("/hello", (req, res) -> {
            String name = req.getValues("name");
            if (name == null || name.isBlank()) {
                name = "world";
            }
            return "Hello " + name;
        });

        HttpServer.get("/pi", (req, res) -> String.valueOf(Math.PI));

        HttpServer.get("/time", (req, res) -> Instant.now().toString());

        HttpServer.get("/health", (req, res) -> {
            res.contentType("application/json; charset=UTF-8");
            return "{\"status\":\"UP\"}";
        });

        serverThread = new Thread(() -> {
            try {
                HttpServer.start(testPort);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, "integration-test-server");
        serverThread.setDaemon(true);
        serverThread.start();

        waitForServerStart();
    }

    @AfterAll
    static void shutdownServer() throws InterruptedException {
        HttpServer.stop();
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    @Test
    void shouldServeHelloEndpoint() throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/App/hello?name=Maria"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response = CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Hello Maria", response.body());
    }

    @Test
    void shouldServeStaticIndex() throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/index.html"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response = CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Welcome to the Microframeworks Web Framework"));
    }

    @Test
    void shouldServeHealthEndpointAsJson() throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/App/health"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response = CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"UP\"}", response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
    }

    @Test
    void shouldServeTimeEndpointAsIsoInstant() throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/App/time"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response = CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("T"));
        assertTrue(response.body().endsWith("Z"));
    }

    @Test
    void shouldHandleConcurrentRequests() throws InterruptedException, ExecutionException {
        int requestCount = 30;
        List<CompletableFuture<java.net.http.HttpResponse<String>>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            int id = i;
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/App/hello?name=User" + id))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

                futures.add(CLIENT.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        for (int i = 0; i < requestCount; i++) {
            java.net.http.HttpResponse<String> response = futures.get(i).get();
            assertEquals(200, response.statusCode());
            assertEquals("Hello User" + i, response.body());
        }
    }

    private static void waitForServerStart() throws InterruptedException {
        int retries = 40;
        for (int i = 0; i < retries; i++) {
            if (HttpServer.isRunning()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Server did not start in time for integration tests");
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate test port", e);
        }
    }
}
