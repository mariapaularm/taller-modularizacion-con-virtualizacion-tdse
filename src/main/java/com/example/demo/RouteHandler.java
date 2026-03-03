package com.example.demo;

/**
 * Functional interface for handling HTTP route requests.
 * Enables defining REST endpoints using lambda expressions.
 *
 * Example usage:
 *   get("/hello", (req, res) -> "Hello World");
 */
@FunctionalInterface
public interface RouteHandler {
    String handle(HttpRequest req, HttpResponse res);
}
