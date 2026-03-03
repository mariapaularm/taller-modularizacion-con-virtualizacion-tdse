package com.example.demo.app;

import com.example.demo.HttpServer;

import java.io.IOException;
import java.net.URISyntaxException;

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

        get("/hello", (req, res) ->
                "Hello " + req.getValues("name")
        );

        get("/pi", (req, res) ->
                String.valueOf(Math.PI)
        );

        HttpServer.main(args);
    }
}
