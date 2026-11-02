package dev.truwty.nocturne.lyrics;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class ProviderBackoffTest {
 @Test void acceptsSecondsDatesAndMalformedHeadersWithoutRetryStorms() {
  var now=Instant.parse("2026-11-02T00:00:00Z");
  assertEquals(now.plusSeconds(120),ProviderBackoff.retryAt("120",now));
  assertEquals(Instant.parse("2026-11-02T00:02:00Z"),ProviderBackoff.retryAt("Mon, 02 Nov 2026 00:02:00 GMT",now));
  assertEquals(now.plusSeconds(60),ProviderBackoff.retryAt("invalid",now));
  assertEquals(now.plusSeconds(1),ProviderBackoff.retryAt("-1",now));
 }
}
