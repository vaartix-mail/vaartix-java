# Vaartix Java SDK

The official Java client for [Vaartix](https://vaartix.com), the transactional email API built in India.
Data stored in India (AWS Mumbai), pricing in ₹.

Java 17 or newer. One dependency: Jackson.

```xml
<dependency>
  <groupId>com.vaartix</groupId>
  <artifactId>vaartix-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("com.vaartix:vaartix-java:0.1.0")`

## Send an email

```java
import com.vaartix.SendEmailRequest;
import com.vaartix.Vaartix;

Vaartix vaartix = new Vaartix(); // reads VAARTIX_API_KEY

String id = vaartix.emails().send(new SendEmailRequest()
    .from("Acme <orders@acme.in>")          // an address on a domain verified in Vaartix
    .to("priya@example.com")
    .subject("Your order is confirmed")
    .html("<p>Thanks for your order!</p>")).id();
```

Testing? Send to `delivered@test.vaartix.com` (or `bounced@` / `complained@`). Nothing real is sent.

The client is thread-safe: create one and reuse it (for example as a Spring bean).

## Errors

Failures throw `VaartixException` (unchecked), with the API's `getType()`, `getMessage()` and `getStatusCode()`:

```java
try {
  vaartix.emails().send(email);
} catch (VaartixException e) {
  if (e.getType().equals("validation_error")) { /* fix the request */ }
}
```

`getType()` is `network_error` (and `getStatusCode()` is 0) when no response was received.

## More sending options

```java
vaartix.emails().send(email, "receipt-10482");             // idempotency key: safe retries for 24 hours
email.scheduledAt("2026-11-15T10:00:00+05:30");             // send later
vaartix.batch().send(List.of(a, b));                        // up to 100 emails in one request
email.tracking(true, false);                                // opens on, clicks off for this email
```

Keep click tracking off for login codes, password resets and magic links.

## Read emails

Needs a full-access key.

```java
Email email = vaartix.emails().get("em_...");               // content, status and events
ListEmailsResponse page = vaartix.emails().list(50, null);
vaartix.emails().cancel("em_...");                          // a scheduled email
```

## Webhooks

Check that a webhook request really came from Vaartix before you trust it. Use the **raw** body.

```java
// Spring
@PostMapping("/webhooks/vaartix")
ResponseEntity<Void> webhook(@RequestBody String body, @RequestHeader Map<String, String> headers) {
  try {
    WebhookVerifier.Payload event = WebhookVerifier.verify(body, headers, System.getenv("VAARTIX_WEBHOOK_SECRET"));
    if (event.type().equals(WebhookVerifier.EMAIL_BOUNCED)) {
      String reply = event.data().details().path("diagnosticCode").asText(); // the receiving server's reply
    }
    return ResponseEntity.ok().build();
  } catch (WebhookVerifier.VerificationException e) {
    return ResponseEntity.badRequest().build();
  }
}
```

Signatures follow the [Standard Webhooks](https://www.standardwebhooks.com) spec. Manage endpoints with
`vaartix.webhooks().create`, `list`, `get`, `update`, `remove`, `rotateSecret`, `deliveries` and `retryDelivery`.

## Domains

```java
vaartix.domains().update(domainId, new Models.UpdateDomainRequest(true, null)); // open tracking on
```

## Docs

- Docs: https://vaartix.com/docs
- API reference: https://vaartix.com/docs/api
- Status: https://status.vaartix.com

## Contributing and security

Issues and pull requests are welcome. Report security problems privately, see [SECURITY.md](SECURITY.md).

## License

MIT
