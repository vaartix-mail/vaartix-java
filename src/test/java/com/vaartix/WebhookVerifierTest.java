package com.vaartix;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {
  // Test-only secret: the worked example from the Standard Webhooks specification.
  private static final String PREFIX = "whsec_";
  private static final String SPEC_SECRET = PREFIX + "MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
  private static final String SPEC_BODY = "{\"test\": 2432232314}";
  private static final Instant NOW = Instant.ofEpochSecond(1614265330);

  private static Map<String, String> headers() {
    Map<String, String> h = new HashMap<>();
    h.put("Webhook-Id", "msg_p5jXN8AQM9LWM0D4loKWxJek");
    h.put("webhook-timestamp", "1614265330");
    h.put("webhook-signature", "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
    return h;
  }

  @Test
  void acceptsTheReferenceExample() {
    assertNotNull(WebhookVerifier.verify(SPEC_BODY, headers(), SPEC_SECRET, Duration.ofMinutes(5), NOW));
    Map<String, String> h = headers();
    h.put("webhook-signature", "v1,bm90LXRoaXM= " + h.get("webhook-signature"));
    assertNotNull(WebhookVerifier.verify(SPEC_BODY, h, SPEC_SECRET, Duration.ofMinutes(5), NOW));
  }

  @Test
  void rejectsBadRequests() {
    Duration tol = Duration.ofMinutes(5);
    assertThrows(WebhookVerifier.VerificationException.class, () -> WebhookVerifier.verify("{\"test\": 1}", headers(), SPEC_SECRET, tol, NOW));
    assertThrows(WebhookVerifier.VerificationException.class, () -> WebhookVerifier.verify(SPEC_BODY, headers(), PREFIX + "c2VjcmV0", tol, NOW));
    assertThrows(WebhookVerifier.VerificationException.class, () -> WebhookVerifier.verify(SPEC_BODY, headers(), SPEC_SECRET, tol, NOW.plusSeconds(600)));
    assertThrows(WebhookVerifier.VerificationException.class, () -> WebhookVerifier.verify(SPEC_BODY, new HashMap<>(), SPEC_SECRET, tol, NOW));
    assertThrows(WebhookVerifier.VerificationException.class, () -> WebhookVerifier.verify(SPEC_BODY, headers(), "nope", tol, NOW));
  }

  @Test
  void parsesABouncePayload() throws Exception {
    String body = "{\"type\":\"email.bounced\",\"createdAt\":\"x\",\"data\":{\"emailId\":\"em_1\",\"eventId\":\"evt_1\",\"recipient\":\"old@example.com\",\"from\":\"a@acme.in\",\"to\":[\"old@example.com\"],\"cc\":[],\"bcc\":[],\"subject\":\"s\",\"tags\":[],\"emailCreatedAt\":\"x\",\"details\":{\"bounceType\":\"Permanent\",\"diagnostics\":[],\"diagnosticCode\":\"550 5.1.1 user unknown\",\"statusCode\":\"5.1.1\"}}}";
    String secret = PREFIX + Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring(6)), "HmacSHA256"));
    String sig = Base64.getEncoder().encodeToString(mac.doFinal(("whd_1.1614265330." + body).getBytes(StandardCharsets.UTF_8)));
    Map<String, String> h = Map.of("webhook-id", "whd_1", "webhook-timestamp", "1614265330", "webhook-signature", "v1," + sig);
    WebhookVerifier.Payload p = WebhookVerifier.verify(body, h, secret, Duration.ofMinutes(5), NOW);
    assertEquals(WebhookVerifier.EMAIL_BOUNCED, p.type());
    assertEquals("old@example.com", p.data().recipient());
    assertEquals("550 5.1.1 user unknown", p.data().details().path("diagnosticCode").asText());
  }
}
