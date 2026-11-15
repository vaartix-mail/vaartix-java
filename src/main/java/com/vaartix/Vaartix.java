package com.vaartix;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaartix.Models.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The Vaartix client. Create one and reuse it; it is thread-safe.
 *
 * <pre>{@code
 * Vaartix vaartix = new Vaartix(); // reads VAARTIX_API_KEY
 * String id = vaartix.emails().send(new SendEmailRequest()
 *     .from("Acme <orders@acme.in>").to("priya@example.com")
 *     .subject("Your order is confirmed").html("<p>Thanks!</p>")).id();
 * }</pre>
 */
public final class Vaartix {
  /** This SDK's version, sent in the User-Agent header. */
  public static final String VERSION = "0.1.0";
  /** The Vaartix API. */
  public static final String DEFAULT_BASE_URL = "https://api.vaartix.com";

  static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final String apiKey;
  private final String baseUrl;
  private final HttpClient http;
  private final Duration timeout;
  private final Emails emails = new Emails();
  private final Batch batch = new Batch();
  private final Domains domains = new Domains();
  private final Webhooks webhooks = new Webhooks();

  /** Reads the API key from the VAARTIX_API_KEY environment variable. */
  public Vaartix() { this(null); }

  public Vaartix(String apiKey) { this(apiKey, DEFAULT_BASE_URL, HttpClient.newHttpClient(), Duration.ofSeconds(30)); }

  /** Full control: another base URL (e.g. a local mock), your own HttpClient and a request timeout. */
  public Vaartix(String apiKey, String baseUrl, HttpClient http, Duration timeout) {
    String key = apiKey != null && !apiKey.isEmpty() ? apiKey : System.getenv("VAARTIX_API_KEY");
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Missing API key. Pass it to new Vaartix(\"vx_...\") or set VAARTIX_API_KEY.");
    }
    this.apiKey = key;
    String u = baseUrl;
    while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
    this.baseUrl = u;
    this.http = http;
    this.timeout = timeout;
  }

  public Emails emails() { return emails; }
  public Batch batch() { return batch; }
  public Domains domains() { return domains; }
  public Webhooks webhooks() { return webhooks; }

  static String esc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }

  <T> T request(String method, String path, Object body, String idempotencyKey, JavaType type) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
        .header("Authorization", "Bearer " + apiKey)
        .header("User-Agent", "vaartix-java/" + VERSION)
        .header("Accept", "application/json");
    if (idempotencyKey != null && !idempotencyKey.isEmpty()) b.header("Idempotency-Key", idempotencyKey);
    try {
      if (body != null) {
        b.header("Content-Type", "application/json");
        b.method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
      } else {
        b.method(method, HttpRequest.BodyPublishers.noBody());
      }
    } catch (IOException e) {
      throw new VaartixException("invalid_request", "Could not encode the request: " + e.getMessage(), 0);
    }
    HttpResponse<byte[]> res;
    try {
      res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new VaartixException("network_error", String.valueOf(e.getMessage()), 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new VaartixException("network_error", "Interrupted", 0);
    }
    int status = res.statusCode();
    if (status < 200 || status > 299) {
      String errType = "api_error";
      String message = "Request failed with status " + status;
      try {
        JsonNode err = JSON.readTree(res.body()).path("error");
        if (err.hasNonNull("message")) message = err.get("message").asText();
        if (err.hasNonNull("type")) errType = err.get("type").asText();
      } catch (IOException ignored) {
        // not JSON: keep the generic message
      }
      throw new VaartixException(errType, message, status);
    }
    if (type == null || res.body().length == 0) return null;
    try {
      return JSON.readValue(res.body(), type);
    } catch (IOException e) {
      throw new VaartixException("api_error", "Could not read the response: " + e.getMessage(), status);
    }
  }

  private JavaType t(Class<?> c) { return JSON.getTypeFactory().constructType(c); }

  private JavaType listOf(Class<?> c) {
    return JSON.getTypeFactory().constructParametricType(DataList.class, c);
  }

  /** Send and read emails. */
  public final class Emails {
    private Emails() {}

    /** Send one email. */
    public SendEmailResponse send(SendEmailRequest email) { return send(email, null); }

    /** Send one email; retrying with the same idempotency key within 24 hours returns the first result. */
    public SendEmailResponse send(SendEmailRequest email, String idempotencyKey) {
      return request("POST", "/emails", email, idempotencyKey, t(SendEmailResponse.class));
    }

    /** One email with its events. Needs a full-access key. */
    public Email get(String id) { return request("GET", "/emails/" + esc(id), null, null, t(Email.class)); }

    /** Recent emails, newest first. limit 0 uses the default (20); before pages back from an email ID. */
    public ListEmailsResponse list(int limit, String before) {
      StringBuilder q = new StringBuilder();
      if (limit > 0) q.append("limit=").append(limit);
      if (before != null && !before.isEmpty()) q.append(q.length() > 0 ? "&" : "").append("before=").append(esc(before));
      return request("GET", "/emails" + (q.length() > 0 ? "?" + q : ""), null, null, t(ListEmailsResponse.class));
    }

    /** Cancel a scheduled email before it is sent. Needs a full-access key. */
    public EmailSummary cancel(String id) {
      return request("POST", "/emails/" + esc(id) + "/cancel", null, null, t(EmailSummary.class));
    }
  }

  /** Send many emails in one request. */
  public final class Batch {
    private Batch() {}

    /** Up to 100 emails (no attachments or scheduling). All or nothing. */
    public List<SendEmailResponse> send(List<SendEmailRequest> emails) {
      DataList<SendEmailResponse> out = request("POST", "/emails/batch", emails, null, listOf(SendEmailResponse.class));
      return out.data();
    }
  }

  /** Sending domains. Changes need a full-access key from an owner or admin. */
  public final class Domains {
    private Domains() {}

    /** Change a domain's default open and click tracking. Null fields keep their value. */
    public Domain update(String id, UpdateDomainRequest update) {
      JsonNode raw = request("PATCH", "/domains/" + esc(id), update, null, t(JsonNode.class));
      return new Domain(raw.path("id").asText(), raw.path("openTracking").asBoolean(), raw.path("clickTracking").asBoolean(), raw);
    }
  }

  /** Webhook endpoints. Needs a full-access key. To check incoming requests, see {@link WebhookVerifier}. */
  public final class Webhooks {
    private Webhooks() {}

    /** Create an endpoint. The response includes the signing secret, shown only this once. */
    public Webhook create(CreateWebhookRequest create) { return request("POST", "/webhooks", create, null, t(Webhook.class)); }

    public List<Webhook> list() {
      DataList<Webhook> out = request("GET", "/webhooks", null, null, listOf(Webhook.class));
      return out.data();
    }

    public Webhook get(String id) { return request("GET", "/webhooks/" + esc(id), null, null, t(Webhook.class)); }

    /** Change the URL or events, or turn the endpoint off ("disabled") and on ("enabled"). */
    public Webhook update(String id, UpdateWebhookRequest update) {
      return request("PATCH", "/webhooks/" + esc(id), update, null, t(Webhook.class));
    }

    public void remove(String id) { request("DELETE", "/webhooks/" + esc(id), null, null, null); }

    /** Replace the signing secret. The old one stops working immediately. */
    public Webhook rotateSecret(String id) {
      return request("POST", "/webhooks/" + esc(id) + "/rotate-secret", null, null, t(Webhook.class));
    }

    /** Recent deliveries, newest first. limit 0 uses the default. */
    public List<WebhookDelivery> deliveries(String id, int limit) {
      String path = "/webhooks/" + esc(id) + "/deliveries" + (limit > 0 ? "?limit=" + limit : "");
      DataList<WebhookDelivery> out = request("GET", path, null, null, listOf(WebhookDelivery.class));
      return out.data();
    }

    /** Send a delivery again, with the same webhook-id. */
    public WebhookDelivery retryDelivery(String id, String deliveryId) {
      return request("POST", "/webhooks/" + esc(id) + "/deliveries/" + esc(deliveryId) + "/retry", null, null,
          t(WebhookDelivery.class));
    }
  }
}
