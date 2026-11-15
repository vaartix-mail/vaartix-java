package com.vaartix;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One email. Set html or text (or both).
 *
 * <pre>{@code
 * new SendEmailRequest()
 *     .from("Acme <orders@acme.in>")
 *     .to("priya@example.com")
 *     .subject("Your order is confirmed")
 *     .html("<p>Thanks for your order!</p>");
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SendEmailRequest {
  public String from;
  public List<String> to = new ArrayList<>();
  public String subject;
  public String html;
  public String text;
  public List<String> cc = new ArrayList<>();
  public List<String> bcc = new ArrayList<>();
  public List<String> replyTo = new ArrayList<>();
  public Map<String, String> headers = new LinkedHashMap<>();
  public List<Models.Tag> tags = new ArrayList<>();
  public List<Models.Attachment> attachments = new ArrayList<>();
  /** ISO 8601 date-time to send later, e.g. {@code 2026-11-01T09:00:00+05:30}. */
  public String scheduledAt;
  /** Open and click tracking for this email, overriding the domain's defaults. */
  public Models.Tracking tracking;

  public SendEmailRequest from(String from) { this.from = from; return this; }
  public SendEmailRequest to(String... to) { this.to.addAll(List.of(to)); return this; }
  public SendEmailRequest subject(String subject) { this.subject = subject; return this; }
  public SendEmailRequest html(String html) { this.html = html; return this; }
  public SendEmailRequest text(String text) { this.text = text; return this; }
  public SendEmailRequest cc(String... cc) { this.cc.addAll(List.of(cc)); return this; }
  public SendEmailRequest bcc(String... bcc) { this.bcc.addAll(List.of(bcc)); return this; }
  public SendEmailRequest replyTo(String... replyTo) { this.replyTo.addAll(List.of(replyTo)); return this; }
  public SendEmailRequest header(String name, String value) { this.headers.put(name, value); return this; }
  public SendEmailRequest tag(String name, String value) { this.tags.add(new Models.Tag(name, value)); return this; }
  public SendEmailRequest attachment(Models.Attachment attachment) { this.attachments.add(attachment); return this; }
  public SendEmailRequest scheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; return this; }
  public SendEmailRequest tracking(Boolean opens, Boolean clicks) { this.tracking = new Models.Tracking(opens, clicks); return this; }
}
