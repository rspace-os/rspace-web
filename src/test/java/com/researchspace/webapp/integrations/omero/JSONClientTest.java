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
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.wellsForPlateJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.testutils.StubHttpServer;
import jakarta.json.JsonObject;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JSONClientTest {
  private static StubHttpServer mockServer;
  private static String baseUrl;
  private JSONClient jsonClient;

  private StubHttpServer client;

  @BeforeAll
  public static void startServer() throws java.io.IOException {
    mockServer = new StubHttpServer();
    baseUrl = "http://localhost:" + mockServer.getPort();
  }

  // canned fixtures embed this placeholder in place of a real port; substitute this run's port
  private static String withPort(String json) {
    return json.replace("__MOCKSERVER_PORT__", String.valueOf(mockServer.getPort()));
  }

  @AfterAll
  public static void stopServer() {
    mockServer.stop();
    mockServer = null;
  }

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    client = mockServer;
    client.reset();
    client.get("/api").once().respond(withPort(versionJson));
    client.get("/api/v0").respond(withPort(urlsJson));
    jsonClient = new JSONClient(baseUrl);
  }

  @Test
  public void testJsonClientCreation() {
    assertEquals(baseUrl, jsonClient.getRootUrl());
  }

  @SneakyThrows
  @Test
  public void testListProjects() {
    client
        .get("/api/v0/m/projects/")
        .query("childCount", "true")
        .query("offset", "0")
        .respond(projectsJson);
    Collection<JsonObject> projects = jsonClient.listProjects();
    assertEquals(113, projects.size());
  }

  @SneakyThrows
  @Test
  public void testListScreens() {
    client
        .get("/api/v0/m/screens/")
        .query("childCount", "true")
        .query("offset", "0")
        .respond(screensJson);
    Collection<JsonObject> screens = jsonClient.listScreens();
    assertEquals(97, screens.size());
  }

  @SneakyThrows
  @Test
  public void testListDatasetsForProject() {
    client.get("/api/v0/m/projects/51").respond(projectJson);
    client
        .get("/webclient/api/datasets/")
        .query("id", "51")
        .query("page", "0")
        .respond(datasetsForProjectJson);
    JsonObject project = jsonClient.getProjectWithId(51L);
    Collection<JsonObject> datasets = jsonClient.listDatasetsForProject(project);
    assertEquals(10, datasets.size());
  }

  @SneakyThrows
  @Test
  public void testListImagesForDataset() {
    client.get("/api/v0/m/datasets/51").respond(withPort(datasetJson));
    client.get("/api/v0/m/datasets/51/images/").query("offset", "0").respond(imagesForDatasetJson);
    JsonObject dataset = jsonClient.getDataSetWithId("51");
    Collection<JsonObject> images = jsonClient.listImagesForDataset(dataset);
    assertEquals(33, images.size());
  }

  @SneakyThrows
  @Test // plate has no actual plate acquisition, so we create a fake
  public void testWellsForFakePlateAcquisition() {
    client.get("/api/v0/m/plates/422").query("childCount", "true").respond(withPort(plateJson));
    client
        .get("/api/v0/m/plates/422/wellsampleindex/0/wells/")
        .query("offset", "0")
        .respond(wellsForPlateJson);
    JsonObject plate = jsonClient.getPlateWithId("422");
    Collection<JsonObject> wells = jsonClient.listWellsForPlateAcquisition(plate, 422L, 0);
    assertEquals(96, wells.size());
  }

  @SneakyThrows
  @Test
  public void testPlatesForScreen() {
    client.get("/api/v0/m/screens/51").respond(screenJson);
    client
        .get("/webclient/api/plates/")
        .query("id", "51")
        .query("page", "0")
        .respond(platesForScreenJson);
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
        .get("/api/v0/m/projects/")
        .query("childCount", "true")
        .query("offset", "0")
        .respond(large_data_1_json);
    client
        .get("/api/v0/m/projects/")
        .query("childCount", "true")
        .query("offset", "200")
        .respond(large_data_2_json);
    client
        .get("/api/v0/m/projects/")
        .query("childCount", "true")
        .query("offset", "400")
        .respond(large_data_3_json);
    Collection<JsonObject> projects = jsonClient.listProjects();
    assertEquals(410, projects.size());
  }

  @SneakyThrows
  @Test
  public void testGetAnnotations() {
    client.get("/webclient/api/annotations/").query("screen", "102").respond(screenAnnotationsJson);
    List<String> annotations = jsonClient.getAnnotations(baseUrl, "screen", 102L);
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
