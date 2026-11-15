package com.vaartix;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Request and response types. */
public final class Models {
  private Models() {}

  /** A name/value label on an email, returned in events and webhooks. */
  public record Tag(String name, String value) {}

  /** A file sent with an email. {@code content} is base64; contentType and contentId are optional. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Attachment(String filename, String content, String contentType, String contentId) {
    public Attachment(String filename, String content) { this(filename, content, null, null); }
  }

  /** Open and click tracking for one email; null fields use the domain's default. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Tracking(Boolean opens, Boolean clicks) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SendEmailResponse(String id) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AttachmentInfo(String filename, String contentType, long size) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmailEvent(String type, String recipient, JsonNode data, String occurredAt) {}

  /** An email as listed. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmailSummary(String id, String from, List<String> to, List<String> cc, List<String> bcc,
      List<String> replyTo, String subject, String status, List<Tag> tags, List<AttachmentInfo> attachments,
      String scheduledAt, String sentAt, String lastEventAt, String error, String createdAt) {}

  /** One email with its content and events. {@code tracking} is null when it uses the domain's defaults. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Email(String id, String from, List<String> to, List<String> cc, List<String> bcc,
      List<String> replyTo, String subject, String status, List<Tag> tags, List<AttachmentInfo> attachments,
      String scheduledAt, String sentAt, String lastEventAt, String error, String createdAt,
      String html, String text, Map<String, String> headers, Tracking tracking, List<EmailEvent> events) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ListEmailsResponse(List<EmailSummary> data, boolean hasMore) {}

  /** A sending domain. Only the tracking fields are typed; {@code raw} holds the full JSON. */
  public record Domain(String id, boolean openTracking, boolean clickTracking, JsonNode raw) {}

  /** Fields left null keep their current value. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateDomainRequest(Boolean openTracking, Boolean clickTracking) {}

  /** A webhook endpoint. {@code secret} (whsec_...) is only returned when created or rotated. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Webhook(String id, String url, List<String> events, String status, String createdAt, String secret) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CreateWebhookRequest(String url, List<String> events) {}

  /** Null fields keep their value; status is "enabled" or "disabled". */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UpdateWebhookRequest(String url, List<String> events, String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WebhookDelivery(String id, String type, String eventId, String status, int attempts,
      Integer responseStatus, String responseBody, String nextAttemptAt, String deliveredAt, String createdAt,
      JsonNode payload) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DataList<T>(List<T> data) {}
}
