package com.example.demo;

import java.net.URL;
import java.net.MalformedURLException;

public class URLParser {
    public static void main(String[] args) throws MalformedURLException {
        URL myurl = new URL("http://is.escuelaing.edu.co:7654/respuestas/respuestas.txt?val=7&t=3#pubs");
        System.out.println(("Protocol: " + myurl.getProtocol()));
        System.out.println(("Host: " + myurl.getHost()));
        System.out.println(("Authority: " + myurl.getAuthority()));
        System.out.println(("Port: " + myurl.getPort()));
        System.out.println(("Path: " + myurl.getPath()));
        System.out.println(("Protocol: " + myurl.getQuery()));
        System.out.println(("Protocol: " + myurl.getFile()));
        System.out.println(("Protocol: " + myurl.getRef()));

    }


}
