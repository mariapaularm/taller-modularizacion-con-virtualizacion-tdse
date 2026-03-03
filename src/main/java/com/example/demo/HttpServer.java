package com.example.demo;

import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal HTTP server that supports REST route handling and static file serving.
 *
 * REST routes are registered via {@link #get(String, RouteHandler)} and accessed
 * under the /App prefix (e.g., route "/hello" → URL "/App/hello").
 *
 * Static files are served from the configured directory via {@link #staticfiles(String)}.
 */
public class HttpServer {

    private static final int PORT = 8080;
    private static final Map<String, RouteHandler> routes = new HashMap<>();
    private static String staticFilesDirectory = "/webroot";

    public static void main(String[] args) throws IOException, URISyntaxException {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + PORT + ".");
            System.exit(1);
        }

        System.out.println("Web Framework Server running on http://localhost:" + PORT);

        Socket clientSocket = null;
        boolean running = true;
        while (running) {
            try {
                System.out.println("Listo para recibir ...");
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                System.err.println("Accept failed.");
                System.exit(1);
            }
            handleRequest(clientSocket);
        }
        serverSocket.close();
    }

    /**
     * Processes a single HTTP request: tries REST routes first, then static files.
     */
    private static void handleRequest(Socket clientSocket) throws IOException, URISyntaxException {
        OutputStream rawOut = clientSocket.getOutputStream();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;

        boolean firstLine = true;
        String reqPath = "";
        String queryString = null;

        while ((inputLine = in.readLine()) != null) {
            System.out.println("Received: " + inputLine);
            if (firstLine) {
                String[] reqTokens = inputLine.split(" ");
                String method = reqTokens[0];
                String strUri = reqTokens[1];
                String protocol = reqTokens[2];

                URI requestUri = new URI(strUri);
                reqPath = requestUri.getPath();
                queryString = requestUri.getQuery();

                System.out.println("Request path: " + reqPath);
                firstLine = false;
            }
            if (!in.ready()) {
                break;
            }
        }

        // --- Routing logic ---
        // REST routes are accessed under /App prefix (e.g., /App/hello → route "/hello")
        String routePath = reqPath;
        if (reqPath.startsWith("/App")) {
            routePath = reqPath.substring(4);
        }

        RouteHandler handler = routes.get(routePath);

        if (handler != null) {
            // REST route matched — create request/response and invoke handler
            HttpRequest req = new HttpRequest(queryString);
            HttpResponse res = new HttpResponse();
            String body = handler.handle(req, res);

            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html\r\n"
                    + "\r\n"
                    + "<!DOCTYPE html>"
                    + "<html>"
                    + "<head>"
                    + "<meta charset=\"UTF-8\">"
                    + "<title>Response</title>"
                    + "</head>"
                    + "<body>"
                    + body
                    + "</body>"
                    + "</html>";
            rawOut.write(response.getBytes());

        } else if (serveStaticFile(reqPath, rawOut)) {
            // Static file served successfully

        } else {
            // No route or static file found — 404
            String response = "HTTP/1.1 404 Not Found\r\n"
                    + "Content-Type: text/html\r\n"
                    + "\r\n"
                    + "<!DOCTYPE html>"
                    + "<html>"
                    + "<head>"
                    + "<meta charset=\"UTF-8\">"
                    + "<title>404 Not Found</title>"
                    + "</head>"
                    + "<body>"
                    + "<h1>404 - Not Found</h1>"
                    + "</body>"
                    + "</html>";
            rawOut.write(response.getBytes());
        }

        rawOut.flush();
        rawOut.close();
        in.close();
        clientSocket.close();
    }

    /**
     * Attempts to serve a static file from the configured directory.
     * @return true if the file was found and served, false otherwise
     */
    private static boolean serveStaticFile(String path, OutputStream out) {
        try {
            String resourcePath = staticFilesDirectory + path;
            InputStream fileStream = HttpServer.class.getResourceAsStream(resourcePath);

            if (fileStream == null) {
                return false;
            }

            byte[] fileBytes = fileStream.readAllBytes();
            fileStream.close();

            String contentType = getContentType(path);
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