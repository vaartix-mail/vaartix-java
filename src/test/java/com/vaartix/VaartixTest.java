package com.vaartix;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import com.vaartix.Models.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VaartixTest {
  private HttpServer server;
  private String method, path, auth, ua, idem;
  private JsonNode body;

  private Vaartix client(int status, String response) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> {
      method = ex.getRequestMethod();
      path = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() != null ? "?" + ex.getRequestURI().getRawQuery() : "");
      auth = ex.getRequestHeaders().getFirst("Authorization");
      ua = ex.getRequestHeaders().getFirst("User-Agent");
      idem = ex.getRequestHeaders().getFirst("Idempotency-Key");
      byte[] in = ex.getRequestBody().readAllBytes();
      body = in.length > 0 ? Vaartix.JSON.readTree(in) : null;
      byte[] out = response.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
      if (out.length > 0) try (OutputStream os = ex.getResponseBody()) { os.write(out); }
      ex.close();
    });
    server.start();
    return new Vaartix("vx_test", "http://127.0.0.1:" + server.getAddress().getPort() + "/", HttpClient.newHttpClient(), Duration.ofSeconds(5));
  }

  @AfterEach
  void stop() { if (server != null) server.stop(0); }

  @Test
  void sendsAnEmail() throws IOException {
    Vaartix v = client(200, "{\"id\":\"em_123\"}");
    SendEmailResponse res = v.emails().send(new SendEmailRequest().from("Acme <orders@acme.in>").to("priya@example.com")
        .subject("Hi").html("<p>Hi</p>").replyTo("help@acme.in").tag("flow", "order").tracking(true, null), "order-1");
    assertEquals("em_123", res.id());
    assertEquals("POST", method);
    assertEquals("/emails", path);
    assertEquals("Bearer vx_test", auth);
    assertEquals("vaartix-java/" + Vaartix.VERSION, ua);
    assertEquals("order-1", idem);
    assertEquals("help@acme.in", body.get("replyTo").get(0).asText());
    assertFalse(body.has("text"));
    assertFalse(body.has("cc"));
    assertTrue(body.get("tracking").get("opens").asBoolean());
    assertFalse(body.get("tracking").has("clicks"));
  }

  @Test
  void apiErrorsAndNetworkErrors() throws IOException {
    Vaartix v = client(422, "{\"error\":{\"type\":\"validation_error\",\"message\":\"to: not a valid email address.\"}}");
    VaartixException e = assertThrows(VaartixException.class, () -> v.emails().send(new SendEmailRequest().from("a@acme.in").to("x").subject("s").text("t")));
    assertEquals("validation_error", e.getType());
    assertEquals(422, e.getStatusCode());
    stop();
    Vaartix v2 = client(500, "oops");
    VaartixException e2 = assertThrows(VaartixException.class, () -> v2.emails().get("em_1"));
    assertEquals("api_error", e2.getType());
    Vaartix dead = new Vaartix("vx_test", "http://127.0.0.1:1", HttpClient.newHttpClient(), Duration.ofSeconds(2));
    VaartixException e3 = assertThrows(VaartixException.class, () -> dead.emails().get("em_1"));
    assertEquals("network_error", e3.getType());
    assertEquals(0, e3.getStatusCode());
  }

  @Test
  void readsListsAndCancels() throws IOException {
    Vaartix v = client(200, "{\"id\":\"em_1\",\"status\":\"delivered\",\"tracking\":{\"opens\":true},\"events\":[{\"type\":\"delivered\",\"recipient\":\"a@b.in\",\"data\":{},\"occurredAt\":\"x\"}],\"future\":1}");
    Email email = v.emails().get("em_1/x");
    assertEquals("delivered", email.status());
    assertTrue(email.tracking().opens());
    assertEquals(1, email.events().size());
    assertEquals("/emails/em_1%2Fx", path);
    stop();
    Vaartix v2 = client(200, "{\"data\":[{\"id\":\"em_2\"}],\"hasMore\":true}");
    ListEmailsResponse page = v2.emails().list(5, "em_9");
    assertTrue(page.hasMore());
    assertEquals("/emails?limit=5&before=em_9", path);
    v2.emails().cancel("em_3");
    assertEquals("/emails/em_3/cancel", path);
  }

  @Test
  void batchAndDomains() throws IOException {
    Vaartix v = client(200, "{\"data\":[{\"id\":\"em_a\"},{\"id\":\"em_b\"}]}");
    List<SendEmailResponse> out = v.batch().send(List.of(new SendEmailRequest().from("a@acme.in").to("x@y.in").subject("1").text("1"),
        new SendEmailRequest().from("a@acme.in").to("z@y.in").subject("2").text("2")));
    assertEquals(2, out.size());
    assertTrue(body.isArray());
    stop();
    Vaartix v2 = client(200, "{\"id\":\"dom_1\",\"name\":\"acme.in\",\"openTracking\":true,\"clickTracking\":false}");
    Domain d = v2.domains().update("dom_1", new UpdateDomainRequest(true, null));
    assertTrue(d.openTracking());
    assertEquals("acme.in", d.raw().get("name").asText());
    assertEquals("PATCH", method);
    assertFalse(body.has("clickTracking"));
  }

  @Test
  void webhooks() throws IOException {
    Vaartix v = client(200, "{\"id\":\"wh_1\",\"url\":\"https://acme.in/h\",\"events\":[\"email.delivered\"],\"status\":\"enabled\",\"secret\":\"whsec_x\"}");
    assertEquals("whsec_x", v.webhooks().create(new CreateWebhookRequest("https://acme.in/h", List.of("email.delivered"))).secret());
    v.webhooks().update("wh_1", new UpdateWebhookRequest(null, null, "disabled"));
    assertEquals("PATCH", method);
    assertFalse(body.has("url"));
    v.webhooks().rotateSecret("wh_1");
    assertEquals("/webhooks/wh_1/rotate-secret", path);
    v.webhooks().retryDelivery("wh_1", "whd_2");
    assertEquals("/webhooks/wh_1/deliveries/whd_2/retry", path);
    stop();
    Vaartix v2 = client(200, "{\"data\":[{\"id\":\"whd_1\",\"status\":\"succeeded\",\"attempts\":1,\"payload\":{\"type\":\"email.sent\"}}]}");
    assertEquals(1, v2.webhooks().deliveries("wh_1", 10).size());
    assertEquals("/webhooks/wh_1/deliveries?limit=10", path);
    stop();
    Vaartix v3 = client(204, "");
    v3.webhooks().remove("wh_1");
    assertEquals("DELETE", method);
  }

  @Test
  void needsAKey() {
    assertThrows(IllegalArgumentException.class, () -> new Vaartix("", "http://x", HttpClient.newHttpClient(), Duration.ofSeconds(1)));
  }
}
