package com.researchspace.webapp.integrations.dbrepo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

public class DBRepoClientTest {

  private DBRepoClient client;
  private MockRestServiceServer server;

  @Before
  public void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    client = new DBRepoClient(restTemplate);
    server = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  public void normalizesHttpAndHttpsUrls() {
    assertEquals(
        "https://dbrepo.example/base", client.normalizeBaseUrl(" https://dbrepo.example/base/ "));
    assertEquals("http://localhost:8080", client.normalizeBaseUrl("http://localhost:8080/"));
  }

  @Test
  public void rejectsUnsupportedUrls() {
    assertThrows(
        IllegalArgumentException.class, () -> client.normalizeBaseUrl("ftp://dbrepo.example"));
    assertThrows(
        IllegalArgumentException.class,
        () -> client.normalizeBaseUrl("https://u:p@dbrepo.example"));
    assertThrows(
        IllegalArgumentException.class,
        () -> client.normalizeBaseUrl("https://dbrepo.example?a=b"));
  }

  @Test
  public void listsDatabasesFromCurrentApi() {
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"db-1\",\"name\":\"Research data\",\"description\":\"Primary\"}]",
                MediaType.APPLICATION_JSON));

    List<DBRepoDatabaseDTO> databases =
        client.listDatabases("https://dbrepo.example", new DBRepoCredentials("user", "pass"));

    assertEquals(1, databases.size());
    assertEquals("db-1", databases.get(0).id());
    assertEquals("Research data", databases.get(0).name());
    assertEquals("Primary", databases.get(0).description());
    assertEquals("https://dbrepo.example/database/db-1", databases.get(0).url());
    server.verify();
  }

  @Test
  public void fallsBackToLegacyApiWhenCurrentApiIsMissing() {
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database"))
        .andRespond(withResourceNotFound());
    server
        .expect(requestTo("https://dbrepo.example/api/database"))
        .andRespond(
            withSuccess("[{\"id\":\"db-2\",\"name\":\"Legacy\"}]", MediaType.APPLICATION_JSON));

    List<DBRepoDatabaseDTO> databases =
        client.listDatabases("https://dbrepo.example", new DBRepoCredentials("user", "pass"));

    assertEquals(1, databases.size());
    assertEquals("db-2", databases.get(0).id());
    server.verify();
  }
}
