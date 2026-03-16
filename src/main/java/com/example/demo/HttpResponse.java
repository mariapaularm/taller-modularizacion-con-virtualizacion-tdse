package com.example.demo;

public class HttpResponse {

	private int statusCode = 200;
	private String reasonPhrase = "OK";
	private String contentType = "text/plain; charset=UTF-8";

	public int getStatusCode() {
		return statusCode;
	}

	public String getReasonPhrase() {
		return reasonPhrase;
	}

	public String getContentType() {
		return contentType;
	}

	public void status(int code, String reason) {
		this.statusCode = code;
		this.reasonPhrase = reason;
	}

	public void contentType(String contentType) {
		this.contentType = contentType;
	}
}
