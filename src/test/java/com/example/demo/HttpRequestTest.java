package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestTest {

    @Test
    void shouldParseQueryParams() {
        HttpRequest request = new HttpRequest("name=Maria&course=TDSE");

        assertEquals("Maria", request.getValues("name"));
        assertEquals("TDSE", request.getValues("course"));
        assertEquals(2, request.getQueryParams().size());
    }

    @Test
    void shouldDecodeEncodedValues() {
        HttpRequest request = new HttpRequest("name=Maria%20Fernanda&city=Bogota%2C%20CO");

        assertEquals("Maria Fernanda", request.getValues("name"));
        assertEquals("Bogota, CO", request.getValues("city"));
    }

    @Test
    void shouldHandleMissingValue() {
        HttpRequest request = new HttpRequest("flag");

        assertEquals("", request.getValues("flag"));
        assertNull(request.getValues("missing"));
    }

    @Test
    void shouldExposeReadOnlyQueryMap() {
        HttpRequest request = new HttpRequest("name=Maria");

        assertTrue(request.getQueryParams().containsKey("name"));
    }
}
