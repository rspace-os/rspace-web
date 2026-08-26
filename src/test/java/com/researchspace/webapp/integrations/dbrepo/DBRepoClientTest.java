package com.researchspace.webapp.integrations.dbrepo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
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

  @Test
  public void listsDatabaseResourcesFromCurrentApi() {
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/table"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"table-1\",\"name\":\"Experiments\"}]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/view"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"view-1\",\"name\":\"Recent experiments\",\"query\":\"SELECT * FROM"
                    + " experiments\"}]",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/subset"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"subset-1\",\"query\":\"SELECT * FROM experiments WHERE status ="
                    + " 'open'\"}]",
                MediaType.APPLICATION_JSON));

    DBRepoDatabaseResourcesDTO resources =
        client.listDatabaseResources(
            "https://dbrepo.example", "db-1", new DBRepoCredentials("user", "pass"));

    assertEquals(1, resources.tables().size());
    assertEquals("table", resources.tables().get(0).type());
    assertEquals("Experiments", resources.tables().get(0).label());
    assertEquals(
        "https://dbrepo.example/database/db-1/table/table-1", resources.tables().get(0).url());
    assertEquals(1, resources.views().size());
    assertEquals("Recent experiments", resources.views().get(0).label());
    assertEquals("SELECT * FROM experiments", resources.views().get(0).secondaryText());
    assertEquals(
        "https://dbrepo.example/database/db-1/view/view-1", resources.views().get(0).url());
    assertEquals(1, resources.subsets().size());
    assertEquals(
        "SELECT * FROM experiments WHERE status = 'open'", resources.subsets().get(0).label());
    assertEquals(
        "https://dbrepo.example/database/db-1/subset/subset-1", resources.subsets().get(0).url());
    assertEquals(List.of(), resources.failedTypes());
    server.verify();
  }

  @Test
  public void keepsSuccessfulDatabaseResourcesWhenOneCategoryFails() {
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/table"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"table-1\",\"name\":\"Experiments\"}]", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/view"))
        .andRespond(withServerError());
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/subset"))
        .andRespond(
            withSuccess(
                "[{\"id\":\"subset-1\",\"query\":\"SELECT 1\"}]", MediaType.APPLICATION_JSON));

    DBRepoDatabaseResourcesDTO resources =
        client.listDatabaseResources(
            "https://dbrepo.example", "db-1", new DBRepoCredentials("user", "pass"));

    assertEquals(1, resources.tables().size());
    assertEquals(0, resources.views().size());
    assertEquals(1, resources.subsets().size());
    assertEquals(List.of("view"), resources.failedTypes());
    server.verify();
  }

  @Test
  public void downloadsResourceCsvFromCurrentApi() {
    server
        .expect(requestTo("https://dbrepo.example/api/v1/database/db-1/view/view-1/data"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
        .andExpect(header(HttpHeaders.ACCEPT, "text/csv"))
        .andRespond(withSuccess("id,name\n1,Experiment\n", MediaType.parseMediaType("text/csv")));

    byte[] csv =
        client.downloadResourceCsv(
            "https://dbrepo.example",
            "db-1",
            "view",
            "view-1",
            new DBRepoCredentials("user", "pass"));

    assertArrayEquals("id,name\n1,Experiment\n".getBytes(StandardCharsets.UTF_8), csv);
    server.verify();
  }

  @Test
  public void rejectsDatabaseCsvDownload() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            client.downloadResourceCsv(
                "https://dbrepo.example",
                "db-1",
                "database",
                "db-1",
                new DBRepoCredentials("user", "pass")));
  }
}
