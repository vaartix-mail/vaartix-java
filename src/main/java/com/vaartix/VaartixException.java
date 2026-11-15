package com.vaartix;

/** The API answered with an error, or the request could not be made. */
public class VaartixException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final String type;
  private final int statusCode;

  public VaartixException(String type, String message, int statusCode) {
    super(message);
    this.type = type;
    this.statusCode = statusCode;
  }

  /** The API's error type, for example {@code validation_error}, or {@code network_error}. */
  public String getType() { return type; }

  /** The HTTP status, or 0 when no response was received. */
  public int getStatusCode() { return statusCode; }
}
