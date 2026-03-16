package com.example.demo.app;

import com.example.demo.HttpServer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;

import static com.example.demo.HttpServer.get;
import static com.example.demo.HttpServer.staticfiles;

/**
 * Example application demonstrating the Microframeworks Web Framework.
 *
 * Defines REST endpoints and configures static file serving.
 *
 * REST endpoints:
 *   GET /App/hello?name=Pedro  → "Hello Pedro"
 *   GET /App/pi                → value of Pi
 *
 * Static files:
 *   GET /index.html            → served from /webroot
 */
public class ExampleApp {

    public static void main(String[] args) throws IOException, URISyntaxException {

        staticfiles("/webroot");

        get("/hello", (req, res) -> {
            String name = req.getValues("name");
            if (name == null || name.isBlank()) {
                name = "world";
            }
            return "Hello " + name;
        });

        get("/time", (req, res) -> Instant.now().toString());

        get("/pi", (req, res) -> String.valueOf(Math.PI));

        get("/health", (req, res) -> {
            res.contentType("application/json; charset=UTF-8");
            return "{\"status\":\"UP\"}";
        });

        HttpServer.start(resolvePort(args));
    }

    private static int resolvePort(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }

        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }

        return 8080;
    }
}
