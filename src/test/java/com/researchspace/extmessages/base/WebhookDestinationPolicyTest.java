package com.researchspace.extmessages.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.extmessages.base.WebhookDestinationPolicy.HostResolver;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookDestinationPolicyTest {

  private static final String TEAMS_HOST = "prod-01.westus.logic.azure.com";

  private final WebhookDestinationPolicy policy =
      WebhookDestinationPolicy.parse(
          "test.allowed.origins",
          "https://hooks.slack.com, https://*.environment.api.powerplatform.com,"
              + "https://*.logic.azure.com,,http://localhost:9099");

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://127.0.0.1:9988/x",
        "https://127.0.0.1/",
        "https://[::1]/",
        "https://169.254.169.254/",
        "https://user:pw@hooks.slack.com/x",
        "http://hooks.slack.com/x",
        "https://hooks.slack.com.evil.example/x",
        "https://evilhooks.slack.com/x",
        "https://hooks.slack.com:8443/x",
        "https://logic.azure.com/x",
        "http://localhost:9098/x",
        "/relative/path",
        "not a url"
      })
  void rejectsUrlsOutsideTheAllowedOrigins(String url) {
    assertThrows(WebhookUrlNotAllowedException.class, () -> policy.checkSyntax(url));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://hooks.slack.com/services/T/B/x",
        "https://HOOKS.slack.com./x",
        "https://default1234.56.environment.api.powerplatform.com:443/powerautomate/x?sig=y",
        "https://" + TEAMS_HOST + ":443/workflows/x/triggers/manual/paths/invoke?sig=y",
        "http://localhost:9099/msteams/webhook"
      })
  void acceptsUrlsOnAllowedOrigins(String url) {
    assertDoesNotThrow(() -> policy.checkSyntax(url));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "10.0.0.5",
        "127.0.0.1",
        "169.254.169.254",
        "fd00::1",
        "0.0.0.0",
        "100.100.100.200",
        "64:ff9b::a00:5"
      })
  void rejectsWildcardHostResolvingToNonPublicAddress(String address) throws Exception {
    HostResolver resolver = resolvingTo(InetAddress.getByName(address));
    assertThrows(
        WebhookUrlNotAllowedException.class,
        () -> policy.checkResolved("https://" + TEAMS_HOST + "/x", resolver));
  }

  @Test
  void rejectsWildcardHostResolvingToIpv4MappedLoopback() throws Exception {
    byte[] mapped = new byte[16];
    mapped[10] = (byte) 0xff;
    mapped[11] = (byte) 0xff;
    mapped[12] = 127;
    mapped[15] = 1;
    HostResolver resolver = resolvingTo(Inet6Address.getByAddress(null, mapped, -1));
    assertThrows(
        WebhookUrlNotAllowedException.class,
        () -> policy.checkResolved("https://" + TEAMS_HOST + "/x", resolver));
  }

  @Test
  void rejectsWildcardHostWithAnyNonPublicAddress() throws Exception {
    HostResolver resolver =
        resolvingTo(InetAddress.getByName("20.42.0.1"), InetAddress.getByName("10.0.0.5"));
    assertThrows(
        WebhookUrlNotAllowedException.class,
        () -> policy.checkResolved("https://" + TEAMS_HOST + "/x", resolver));
  }

  @Test
  void acceptsWildcardHostResolvingToPublicAddress() throws Exception {
    HostResolver resolver = resolvingTo(InetAddress.getByName("20.42.0.1"));
    assertDoesNotThrow(() -> policy.checkResolved("https://" + TEAMS_HOST + "/x", resolver));
  }

  @Test
  void neverResolvesExactEntries() {
    HostResolver resolver = mock(HostResolver.class);
    assertDoesNotThrow(
        () -> policy.checkResolved("http://localhost:9099/msteams/webhook", resolver));
    verifyNoInteractions(resolver);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "hooks.slack.com",
        "ftp://hooks.slack.com",
        "https://h.example/path",
        "https://user@h.example",
        "https://h.example?sig=x",
        "https://h.example#x",
        "https://h.*.example",
        "https://",
        "https://[123]",
        "https://*.10.0.0.1",
        "https://*.0.0.1",
        "https://*.[::1]"
      })
  void malformedEntryStopsStartup(String entry) {
    assertThrows(
        IllegalStateException.class, () -> WebhookDestinationPolicy.parse("test.origins", entry));
  }

  private static HostResolver resolvingTo(InetAddress... addresses) throws UnknownHostException {
    HostResolver resolver = mock(HostResolver.class);
    when(resolver.resolve(TEAMS_HOST)).thenReturn(addresses);
    return resolver;
  }
}
