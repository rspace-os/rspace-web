package com.researchspace.webapp.integrations.omero;

import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.datasetJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.datasetsForProjectJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.imagesForDatasetJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.large_data_1_json;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.large_data_2_json;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.large_data_3_json;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.plateJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.platesForScreenJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.projectJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.projectsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screenAnnotationsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screenJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screensJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.urlsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.versionJson;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.Collection;
import java.util.List;
import javax.json.JsonObject;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;

public class JSONClientTest {
  private static ClientAndServer mockServer;
  private JSONClient jsonClient;
  private String baseUrl = "http://localhost:1080";

  private MockServerClient client;

  @BeforeAll
  public static void startServer() {
    mockServer = ClientAndServer.startClientAndServer(1080);
  }

  @AfterAll
  public static void stopServer() {
    mockServer.stop();
    mockServer = null;
  }

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    client = new MockServerClient("localhost", 1080);
    client.reset();
    client
        .when(request().withMethod("GET").withPath("/api"), Times.exactly(1))
        .respond(response().withBody(versionJson));
    client
        .when(request().withMethod("GET").withPath("/api/v0"))
        .respond(response().withBody(urlsJson));
    jsonClient = new JSONClient(baseUrl);
  }

  @Test
  public void testJsonClientCreation() {
    assertEquals("http://localhost:1080", jsonClient.getRootUrl());
  }

  @SneakyThrows
  @Test
  public void testListProjects() {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/projects/")
                .withQueryStringParameter("childCount", "true")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(projectsJson));
    Collection<JsonObject> projects = jsonClient.listProjects();
    assertEquals(113, projects.size());
  }

  @SneakyThrows
  @Test
  public void testListScreens() {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/screens/")
                .withQueryStringParameter("childCount", "true")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(screensJson));
    Collection<JsonObject> screens = jsonClient.listScreens();
    assertEquals(97, screens.size());
  }

  @SneakyThrows
  @Test
  public void testListDatasetsForProject() {
    client
        .when(request().withMethod("GET").withPath("/api/v0/m/projects/51"))
        .respond(response().withBody(projectJson));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/api/datasets/")
                .withQueryStringParameter("id", "51")
                .withQueryStringParameter("page", "0"))
        .respond(response().withBody(datasetsForProjectJson));
    JsonObject project = jsonClient.getProjectWithId(51L);
    Collection<JsonObject> datasets = jsonClient.listDatasetsForProject(project);
    assertEquals(10, datasets.size());
  }

  @SneakyThrows
  @Test
  public void testListImagesForDataset() {
    client
        .when(request().withMethod("GET").withPath("/api/v0/m/datasets/51"))
        .respond(response().withBody(datasetJson));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/datasets/51/images/")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(imagesForDatasetJson));
    JsonObject dataset = jsonClient.getDataSetWithId("51");
    Collection<JsonObject> images = jsonClient.listImagesForDataset(dataset);
    assertEquals(33, images.size());
  }

  @SneakyThrows
  @Test // plate has no actual plate acquisition, so we create a fake
  public void testWellsForFakePlateAcquisition() {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/plates/422")
                .withQueryStringParameter("childCount", "true"))
        .respond(response().withBody(plateJson));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/plates/422/wellsampleindex/0/wells/")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(imagesForDatasetJson));
    JsonObject plate = jsonClient.getPlateWithId("422");
    Collection<JsonObject> wells = jsonClient.listWellsForPlateAcquisition(plate, 422L, 0);
    assertEquals(96, wells.size());
  }

  @SneakyThrows
  @Test
  public void testPlatesForScreen() {
    client
        .when(request().withMethod("GET").withPath("/api/v0/m/screens/51"))
        .respond(response().withBody(screenJson));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/api/plates/")
                .withQueryStringParameter("id", "51")
                .withQueryStringParameter("page", "0"))
        .respond(response().withBody(platesForScreenJson));
    JsonObject screen = jsonClient.getScreenWithId(51L);
    Collection<JsonObject> plates = jsonClient.listPlatesForScreen(screen);
    assertEquals(85, plates.size());
  }

  // All data responses larger than 'limit' (omero sets limit at 200), are handled by common code.
  // Therefore this test covers the 'getBatchesOfData' functionality in JsonClient
  @SneakyThrows
  @Test
  public void testListLargeData() {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/projects/")
                .withQueryStringParameter("childCount", "true")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(large_data_1_json));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/projects/")
                .withQueryStringParameter("childCount", "true")
                .withQueryStringParameter("offset", "200"))
        .respond(response().withBody(large_data_2_json));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/api/v0/m/projects/")
                .withQueryStringParameter("childCount", "true")
                .withQueryStringParameter("offset", "400"))
        .respond(response().withBody(large_data_3_json));
    Collection<JsonObject> projects = jsonClient.listProjects();
    assertEquals(410, projects.size());
  }

  @SneakyThrows
  @Test
  public void testGetAnnotations() {
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/api/annotations/")
                .withQueryStringParameter("screen", "102"))
        .respond(response().withBody(screenAnnotationsJson));
    List<String> annotations = jsonClient.getAnnotations("http://localhost:1080", "screen", 102L);
    assertEquals(18, annotations.size());
    List<String> expected =
        List.of(
            "Sample Type = cell",
            "Organism = Homo sapiens",
            "Study Title = Focused mitotic chromsome condensaton screen using HeLa cells",
            "Study Type = high content screen",
            "Screen Type = primary screen",
            "Screen Technology Type = RNAi screen",
            "Imaging Method = fluorescence microscopy",
            "Publication Title = Integration of biological data by kernels on graph nodes allows"
                + " prediction of new genes involved in mitotic chromosome condensation.",
            "Publication Authors = Hériché JK, Lees JG, Morilla I, Walter T, Petrova B, Roberti MJ,"
                + " Hossain MJ, Adler P, Fernández JM, Krallinger M, Haering CH, Vilo J, Valencia"
                + " A, Ranea JA, Orengo C, Ellenberg J",
            "PubMed ID = 24943848 https://www.ncbi.nlm.nih.gov/pubmed/24943848",
            "PMC ID = PMC4142622 https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4142622",
            "Publication DOI = 10.1091/mbc.E13-04-0221 https://doi.org/10.1091/mbc.E13-04-0221",
            "Release Date = 2016-05-26",
            "License = CC BY 4.0 https://creativecommons.org/licenses/by/4.0/",
            "Copyright = Heriche et al",
            "Annotation File = idr0002-screenA-annotation.csv"
                + " https://github.com/IDR/idr0002-heriche-condensation/blob/HEAD/screenA/idr0002-screenA-annotation.csv",
            "File = \"bulk_annotations\"",
            "File = \"/uod/idr/features/idr0002-heriche-condensation/screenA/tables\"");
    assertEquals(expected, annotations);
  }
}
