package com.vaartix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks that a webhook request really came from Vaartix (Standard Webhooks spec).
 *
 * <pre>{@code
 * WebhookVerifier.Payload event = WebhookVerifier.verify(rawBody, headers, System.getenv("VAARTIX_WEBHOOK_SECRET"));
 * if (event.type().equals(WebhookVerifier.EMAIL_BOUNCED)) {
 *   String reply = event.data().details().path("diagnosticCode").asText();
 * }
 * }</pre>
 */
public final class WebhookVerifier {
  private WebhookVerifier() {}

  public static final String EMAIL_SENT = "email.sent";
  public static final String EMAIL_DELIVERED = "email.delivered";
  public static final String EMAIL_DELIVERY_DELAYED = "email.delivery_delayed";
  public static final String EMAIL_BOUNCED = "email.bounced";
  public static final String EMAIL_COMPLAINED = "email.complained";
  public static final String EMAIL_REJECTED = "email.rejected";
  public static final String EMAIL_RENDERING_FAILURE = "email.rendering_failure";
  public static final String EMAIL_SUPPRESSED = "email.suppressed";
  /** details: machine, machineReason, userAgentFamily, country, asn. */
  public static final String EMAIL_OPENED = "email.opened";
  /** details: as opened, plus link and linkIndex. */
  public static final String EMAIL_CLICKED = "email.clicked";

  /** Default timestamp tolerance. */
  public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

  /** Thrown when a request's signature, timestamp or body is not valid. */
  public static class VerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public VerificationException(String message) { super(message); }
  }

  /** The JSON body Vaartix posts. {@code createdAt} is when the event happened; sort by it. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String type, String createdAt, Data data) {}

  /**
   * {@code recipient} is null when the email had more than one recipient. {@code details} depends on the type:
   * for delays and bounces it has {@code diagnosticCode}, the receiving server's exact reply.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(String emailId, String eventId, String recipient, String from, List<String> to, List<String> cc,
      List<String> bcc, String subject, List<Models.Tag> tags, String emailCreatedAt, JsonNode details) {}

  /** Verify with the default 5-minute tolerance. Header names may be in any case. */
  public static Payload verify(String payload, Map<String, String> headers, String secret) {
    return verify(payload, headers, secret, DEFAULT_TOLERANCE, Instant.now());
  }

  static Payload verify(String payload, Map<String, String> headers, String secret, Duration tolerance, Instant now) {
    String id = header(headers, "webhook-id");
    String ts = header(headers, "webhook-timestamp");
    String sigs = header(headers, "webhook-signature");
    if (id == null || ts == null || sigs == null) {
      throw new VerificationException("Missing webhook-id, webhook-timestamp or webhook-signature header.");
    }
    long sent;
    try {
      sent = Long.parseLong(ts);
    } catch (NumberFormatException e) {
      throw new VerificationException("The webhook timestamp is too old or in the future.");
    }
    if (Math.abs(now.getEpochSecond() - sent) > tolerance.getSeconds()) {
      throw new VerificationException("The webhook timestamp is too old or in the future.");
    }
    if (secret == null || !secret.startsWith("whsec_")) throw new VerificationException("The secret should start with whsec_.");
    byte[] expected;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring(6)), "HmacSHA256"));
      expected = mac.doFinal((id + "." + ts + "." + payload).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new VerificationException("The secret is not valid.");
    }
    boolean ok = false;
    // The header can hold several space-separated signatures, e.g. while a secret is rotated.
    for (String entry : sigs.split(" ")) {
      int comma = entry.indexOf(',');
      if (comma < 0 || !entry.substring(0, comma).equals("v1")) continue;
      try {
        if (MessageDigest.isEqual(Base64.getDecoder().decode(entry.substring(comma + 1)), expected)) {
          ok = true;
          break;
        }
      } catch (IllegalArgumentException ignored) {
        // not base64: try the next one
      }
    }
    if (!ok) throw new VerificationException("The webhook signature does not match.");
    try {
      return Vaartix.JSON.readValue(payload, Payload.class);
    } catch (IOException e) {
      throw new VerificationException("The body is not valid JSON.");
    }
  }

  private static String header(Map<String, String> headers, String name) {
    for (Map.Entry<String, String> e : headers.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
    }
    return null;
  }
}
