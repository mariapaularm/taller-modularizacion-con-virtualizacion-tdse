package com.example.demo;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal HTTP server that supports REST route handling and static file serving.
 *
 * REST routes are registered via {@link #get(String, RouteHandler)} and accessed
 * under the /App prefix (e.g., route "/hello" → URL "/App/hello").
 *
 * Static files are served from the configured directory via {@link #staticfiles(String)}.
 */
public class HttpServer {

    private static final int DEFAULT_PORT = 8080;
    private static final int SO_TIMEOUT_MS = 1000;
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    private static final Map<String, RouteHandler> routes = new ConcurrentHashMap<>();
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private static final Object lifecycleLock = new Object();

    private static volatile String staticFilesDirectory = "/webroot";
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private static volatile ServerSocket serverSocket;
    private static ExecutorService requestExecutor;

    private HttpServer() {
        // Utility class.
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        start(DEFAULT_PORT);
    }

    public static void start() throws IOException {
        start(DEFAULT_PORT);
    }

    public static void start(int port) throws IOException {
        synchronized (lifecycleLock) {
            if (running.get()) {
                throw new IllegalStateException("Server is already running");
            }

            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            serverSocket.setSoTimeout(SO_TIMEOUT_MS);

            requestExecutor = Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors() * 2)
            );

            registerShutdownHookIfNeeded();
            running.set(true);
        }

        System.out.println("Web Framework Server running on http://localhost:" + port);

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                requestExecutor.submit(() -> {
                    try {
                        handleRequest(clientSocket);
                    } catch (Exception e) {
                        System.err.println("Error handling request: " + e.getMessage());
                    }
                });
            } catch (SocketTimeoutException ignored) {
                // Wake up periodically to observe running flag.
            } catch (SocketException e) {
                if (running.get()) {
                    throw e;
                }
            } catch (RejectedExecutionException e) {
                System.err.println("Request rejected because server is shutting down.");
            }
        }

        running.set(false);
        closeServerSocketQuietly();
        shutdownExecutor();
    }

    public static void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        closeServerSocketQuietly();
        shutdownExecutor();
    }

    public static boolean isRunning() {
        return running.get();
    }

    private static void registerShutdownHookIfNeeded() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(HttpServer::stop, "http-server-shutdown"));
        }
    }

    private static void closeServerSocketQuietly() {
        ServerSocket localServerSocket = serverSocket;
        if (localServerSocket != null && !localServerSocket.isClosed()) {
            try {
                localServerSocket.close();
            } catch (IOException ignored) {
                // Best effort during shutdown.
            }
        }
        serverSocket = null;
    }

    private static void shutdownExecutor() {
        ExecutorService localExecutor = requestExecutor;
        if (localExecutor == null) {
            return;
        }

        localExecutor.shutdown();
        try {
            if (!localExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                localExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            localExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            requestExecutor = null;
        }
    }

    /**
     * Processes a single HTTP request: tries REST routes first, then static files.
     */
    private static void handleRequest(Socket clientSocket) throws IOException, URISyntaxException {
        try (Socket socket = clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream rawOut = socket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] requestTokens = requestLine.split(" ");
            if (requestTokens.length < 3) {
                writeSimpleResponse(rawOut, 400, "Bad Request", "Malformed request line");
                return;
            }

            String method = requestTokens[0];
            String strUri = requestTokens[1];

            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                // Consume headers. This server ignores them for now.
            }

            if (!"GET".equalsIgnoreCase(method)) {
                writeSimpleResponse(rawOut, 405, "Method Not Allowed", "Only GET is supported");
                return;
            }

            URI requestUri = new URI(strUri);
            String reqPath = requestUri.getPath();
            String queryString = requestUri.getQuery();

            String routePath = reqPath;
            if (reqPath.startsWith("/App")) {
                routePath = reqPath.substring(4);
            }
            if (routePath.isEmpty()) {
                routePath = "/";
            }

            RouteHandler handler = routes.get(routePath);

            if (handler != null) {
                HttpRequest req = new HttpRequest(queryString);
                HttpResponse res = new HttpResponse();
                String body = handler.handle(req, res);
                writeSimpleResponse(
                        rawOut,
                        res.getStatusCode(),
                        res.getReasonPhrase(),
                        body == null ? "" : body,
                        res.getContentType()
                );
                return;
            }

            if (serveStaticFile(reqPath, rawOut)) {
                return;
            }

            writeSimpleResponse(rawOut, 404, "Not Found", "404 - Not Found");
        }
    }

    /**
     * Attempts to serve a static file from the configured directory.
     * @return true if the file was found and served, false otherwise
     */
    private static boolean serveStaticFile(String path, OutputStream out) {
        try {
            String normalizedPath = normalizeStaticPath(path);
            String resourcePath = staticFilesDirectory + normalizedPath;
            InputStream fileStream = HttpServer.class.getResourceAsStream(resourcePath);

            if (fileStream == null) {
                return false;
            }

            byte[] fileBytes = fileStream.readAllBytes();
            fileStream.close();

            String contentType = getContentType(normalizedPath);
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + fileBytes.length + "\r\n"
                    + "\r\n";

            out.write(header.getBytes());
            out.write(fileBytes);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeStaticPath(String path) {
        String requested = (path == null || path.isBlank() || "/".equals(path)) ? "/index.html" : path;
        String normalized = Paths.get(requested).normalize().toString().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.contains("..")) {
            return "/index.html";
        }
        return normalized;
    }

    private static void writeSimpleResponse(OutputStream out,
                                            int statusCode,
                                            String reasonPhrase,
                                            String body) throws IOException {
        writeSimpleResponse(out, statusCode, reasonPhrase, body, "text/plain; charset=UTF-8");
    }

    private static void writeSimpleResponse(OutputStream out,
                                            int statusCode,
                                            String reasonPhrase,
                                            String body,
                                            String contentType) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    /**
     * Determines the MIME content type based on file extension.
     */
    private static String getContentType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "text/plain";
    }

    // ===================== Framework API =====================

    /**
     * Registers a GET route with a handler lambda.
     * Example: get("/hello", (req, res) -> "Hello World");
     *
     * @param path    the route path (e.g., "/hello")
     * @param handler the lambda that processes the request
     */
    public static void get(String path, RouteHandler handler) {
        routes.put(path, handler);
    }

    /**
     * Sets the directory for serving static files (relative to classpath).
     * Example: staticfiles("/webroot");
     *
     * @param folder the classpath folder (e.g., "/webroot")
     */
    public static void staticfiles(String folder) {
        staticFilesDirectory = folder;
    }
}