package com.researchspace.integrations.omero.service;

import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.datasetJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.datasetsForProjectJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.fakeLoginResponseJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.imagesForDatasetJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.imagesThumbNailsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.imagesThumbNailsRun422Part1Json;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.imagesThumbNailsRun422Part2Json;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.plateAcquisitionsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.plateJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.platesForScreenJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.projectJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.projectsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screenAnnotationsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screenJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.screensJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.serversJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.singleImageJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.tokenJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.urlsJson;
import static com.researchspace.webapp.integrations.omero.OmeroJsonTestMother.versionJson;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.researchspace.integrations.omero.client.OmeroClientImpl;
import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.ProjectRSpaceView;
import com.researchspace.integrations.omero.model.ScreenRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.test.util.ReflectionTestUtils;

public class OmeroServiceTest {
  private static ClientAndServer mockServer;
  private OmeroClientImpl omeroClient = new OmeroClientImpl();
  private OmeroServiceImpl service = new OmeroServiceImpl(omeroClient);
  private String baseUrl = "http://localhost:1080/";
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

  // These tests do not require a real login to Omero as we use canned responses from MockServer and
  // no authentication is required.
  // Token requests and login POST request are mocked just to prevent null pointers in JsonClient
  // code.
  @SneakyThrows
  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(omeroClient, "omeroApiUrl", baseUrl);
    ReflectionTestUtils.setField(omeroClient, "omeroServerName", "omero");
    client = new MockServerClient("localhost", 1080);
    client.reset();
    client
        .when(request().withMethod("GET").withPath("/api"))
        .respond(response().withBody(versionJson));
    client
        .when(request().withMethod("GET").withPath("/api/v0"))
        .respond(response().withBody(urlsJson));
    client
        .when(request().withMethod("GET").withPath("/api/v0/servers/"))
        .respond(response().withBody(serversJson));
    client
        .when(request().withMethod("GET").withPath("/api/v0/token/"))
        .respond(response().withBody(tokenJson));
    client
        .when(request().withMethod("POST").withPath("/api/v0/login/"))
        .respond(response().withBody(fakeLoginResponseJson));
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
    List<? extends OmeroRSpaceView> projects =
        service.getProjectsAndScreens("public_,_public", "Projects");
    assertEquals(113, projects.size());
    ProjectRSpaceView aProject = (ProjectRSpaceView) projects.get(0);
    assertEquals("idr0018-neff-histopathology/experimentA", aProject.getName());
    assertEquals(
        "Experiment Description\n"
            + "Histopathology raw images and annotated tiff files of tissues from mice with 10"
            + " different single gene knockouts.",
        aProject.getDescription());
    assertEquals(101L, aProject.getId().longValue());
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
    List<? extends OmeroRSpaceView> screens =
        service.getProjectsAndScreens("public_,_public", "Screens");
    assertEquals(97, screens.size());
    ScreenRSpaceView aScreen = (ScreenRSpaceView) screens.get(0);
    assertEquals("idr0001-graml-sysgro/screenA", aScreen.getName());
    assertEquals(
        "Publication Title\n"
            + "A genomic Multiprocess survey of machineries that control and link cell shape,"
            + " microtubule organization, and cell-cycle progression.\n"
            + "\n"
            + "Screen Description\n"
            + "Primary screen of fission yeast knock out mutants looking for genes controlling cell"
            + " shape, microtubules, and cell-cycle progression. 262 genes controlling specific"
            + " aspects of those processes are identifed, validated, and functionally annotated.",
        aScreen.getDescription());
    assertEquals(3L, aScreen.getId().longValue());
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
    List<DataSetRSpaceView> datasets = service.getDataSets("public_,_public", 51L);
    DataSetRSpaceView aDataSet = datasets.get(0);
    assertEquals("CDK5RAP2-C", aDataSet.getName());
    assertEquals("", aDataSet.getDescription());
    assertEquals(51L, aDataSet.getId().longValue());
  }

  @SneakyThrows
  @Test
  public void testListPlatesForScreen() {
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
    List<PlateRSpaceView> plates = service.getPlates("public_,_public", 51L);
    PlateRSpaceView aPlate = plates.get(0);
    assertEquals("DTT p1", aPlate.getName());
    assertEquals("", aPlate.getDescription());
    assertEquals(101L, aPlate.getId().longValue());
    assertEquals(1, aPlate.getChildCounts());
    assertEquals(51L, aPlate.getParentId().longValue());
  }

  @SneakyThrows
  @Test
  public void testGetPlateAcquisitions() {
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
                .withPath("/api/v0/m/plates/422/plateacquisitions/")
                .withQueryStringParameter("offset", "0"))
        .respond(response().withBody(plateAcquisitionsJson));

    List<PlateAcquisitionRSpaceView> plates = service.getPlateAcquisitions("public_,_public", 422L);
    PlateAcquisitionRSpaceView aPlateAcquisition = plates.get(0);
    assertEquals("Run 422", aPlateAcquisition.getName());
    assertEquals("", aPlateAcquisition.getDescription());
    assertEquals(12, aPlateAcquisition.getColumns());
    assertEquals(8, aPlateAcquisition.getRows());
    assertEquals(false, aPlateAcquisition.isFake());
    assertEquals(422L, aPlateAcquisition.getId().longValue());
    assertEquals(96, aPlateAcquisition.getChildCounts());
    assertEquals(422L, aPlateAcquisition.getParentId().longValue());
    assertEquals(
        "https://idr.openmicroscopy.org/api/v0/m/plateacquisitions/422/wellsampleindex/0/wells/",
        aPlateAcquisition.getSamplesUrls().get(0));
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
    List<String> annotations = service.getAnnotations("public_,_public", 102L, "screen");
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
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/get_thumbnails/")
                .withQueryStringParameter(
                    "id", "1884807", "1884808", "1884809", "1884810", "1884811", "1884812",
                    "1884813", "1884814", "1884815", "1884816", "1884817", "1884818", "1884819",
                    "1884820", "1884821", "1884822", "1884823", "1884824", "1884825", "1884826",
                    "1884827", "1884828", "1884829", "1884830", "1884831", "1884832", "1884833",
                    "1884834", "1884835", "1884836", "1884837", "1884838", "1884839"))
        .respond(response().withBody(imagesThumbNailsJson));
    List<ImageRSpaceView> images = service.getImages("public_,_public", 51L, false);
    ImageRSpaceView anImage = images.get(0);
    assertEquals("Centrin_PCNT_Cep215_20110506_Fri-1545_0_SIR_PRJ.dv", anImage.getName());
    assertEquals(
        "data:image/jpeg;base64,/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw8KCwkMEQ8SEhEPERATFhwXExQaFRARGCEYGhwdHx8fExciJCIeJBweHx7/2wBDAQUFBQcGBw4ICA4eFBEUHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh7/wAARCABgAGADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD4yooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAoopwbCFdoOcc9xQNDaKKKBBRRRQAUUUUAFFFWry0FtGCZ43k3AMqnOAVDA56dyPwpOSTsaQpTnFzS0W5VoooHWmZhRUlxC8LlWVgM/KSpGR1B59iD+NR0k76obVnZhRRRTEFFFFABRRRQAVIGjEQ+VvNDdexGKjopNCauFAoopjLmp30l9M0sjzMWYuQ77uSADjgeg/IVTooqYxUVZCSsFFFFUMKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP/Z",
        anImage.getBase64ThumbnailData());
    assertEquals(1884807L, anImage.getId().longValue());
    assertEquals(51L, anImage.getParentId().longValue());
    assertEquals(33, images.size());
  }

  @SneakyThrows
  @Test
  public void testGetImage() {
    byte[] fakeRenderedImage = new String("abnbnbn").getBytes();
    client
        .when(request().withMethod("GET").withPath("/api/v0/m/images/1884838"))
        .respond(response().withBody(singleImageJson));
    client
        .when(request().withMethod("GET").withPath("/webgateway/render_thumbnail/1884838/400/"))
        .respond(response().withBody(fakeRenderedImage));
    ImageRSpaceView anImage = service.getImage("public_,_public", 1884838L, 51L, false);
    assertEquals("siControl_N20_Cep215_I_20110411_Mon-1503_0_SIR_PRJ.dv", anImage.getName());
    assertEquals(1884838, anImage.getId().longValue());
    assertEquals("data:image/jpeg;base64,YWJuYm5ibg==", anImage.getBase64ThumbnailData());
  }

  @SneakyThrows
  @Test
  public void testGetWells() {
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
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/get_thumbnails/")
                .withQueryStringParameter("id", "179713"))
        .respond(response().withBody(imagesThumbNailsRun422Part1Json));
    client
        .when(
            request()
                .withMethod("GET")
                .withPath("/webclient/get_thumbnails/")
                .withQueryStringParameter("id", "179712"))
        .respond(response().withBody(imagesThumbNailsRun422Part2Json));
    List<WellRSpaceView> wells = service.getWells("public_,_public", 422L, 422L, false, 0);
    WellRSpaceView aWell = wells.get(0);
    ImageRSpaceView wellImage = (ImageRSpaceView) aWell.getChildren().get(0).getChildren().get(0);
    assertEquals("plate1_1_013 [Well 1, Field 1 (Spot 1)]", wellImage.getName());
    assertEquals("0", aWell.getColumn());
    assertEquals("0", aWell.getRow());
    assertEquals(67070, aWell.getId().longValue());
    assertEquals(96, wells.size());
  }
}
