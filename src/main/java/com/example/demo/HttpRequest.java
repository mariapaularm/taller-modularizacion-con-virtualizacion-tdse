package com.example.demo;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP request with query parameter support.
 * Parses query strings from URLs like: /hello?name=Maria&age=21
 */
public class HttpRequest {

    private final Map<String, String> queryParams;

    public HttpRequest() {
        this.queryParams = new HashMap<>();
    }

    /**
     * Creates a request with parsed query parameters.
     * @param queryString the raw query string (e.g., "name=Maria&age=21")
     */
    public HttpRequest(String queryString) {
        this.queryParams = new HashMap<>();
        if (queryString != null && !queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    queryParams.put(decode(keyValue[0]), decode(keyValue[1]));
                } else if (keyValue.length == 1) {
                    queryParams.put(decode(keyValue[0]), "");
                }
            }
        }
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Returns the value of a query parameter by key.
     * @param key the parameter name
     * @return the parameter value, or null if not present
     */
    public String getValues(String key) {
        return queryParams.get(key);
    }

    public Map<String, String> getQueryParams() {
        return Collections.unmodifiableMap(queryParams);
    }

    /**
     * Kept for backward compatibility.
     */
    public String getValue(String values) {
        return getValues(values);
    }
}
