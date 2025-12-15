package com.researchspace.integrations.omero.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.integrations.omero.client.OmeroClientImpl;
import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.ProjectRSpaceView;
import com.researchspace.integrations.omero.model.ScreenRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import com.researchspace.integrations.omero.model.WellSampleDataRSpaceView;
import java.util.List;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Uses the real public Omero repository hosted by Dundee University. Compares actual json responses
 * to the canned responses collected on 1st June 2023
 */
public class OmeroServiceNightlyTest {
  private OmeroClientImpl omeroClient = new OmeroClientImpl();
  private OmeroServiceImpl service = new OmeroServiceImpl(omeroClient);
  private String baseUrl = "https://idr.openmicroscopy.org/";

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(omeroClient, "omeroApiUrl", baseUrl);
    ReflectionTestUtils.setField(omeroClient, "omeroServerName", "omero");
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListProjects() {
    List<? extends OmeroRSpaceView> projects =
        service.getProjectsAndScreens("public_,_public", "Projects");
    ProjectRSpaceView aProject =
        (ProjectRSpaceView)
            getTargetWithMatchingName(projects, "idr0018-neff-histopathology/experimentA");
    assertEquals("idr0018-neff-histopathology/experimentA", aProject.getName());
    assertTrue(aProject.getChildCounts() > 0);
    assertEquals(
        "Experiment Description\n"
            + "Histopathology raw images and annotated tiff files of tissues from mice with 10"
            + " different single gene knockouts.",
        aProject.getDescription());
    assertEquals(101L, aProject.getId().longValue());
  }

  private OmeroRSpaceView getTargetWithMatchingName(
      List<? extends OmeroRSpaceView> data, String name) {
    return getTargetWithMatchingName(data, name, null);
  }

  private OmeroRSpaceView getTargetWithMatchingName(
      List<? extends OmeroRSpaceView> data,
      String name,
      Function<OmeroRSpaceView, ? extends OmeroRSpaceView> targetExtractor) {
    for (OmeroRSpaceView item : data) {
      if (targetExtractor != null) {
        OmeroRSpaceView extractedItem = targetExtractor.apply(item);
        if (extractedItem.getName().equals(name)) {
          return item;
        }
      }
      if (item.getName().equals(name)) {
        return item;
      }
    }
    return null;
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListScreens() {
    List<? extends OmeroRSpaceView> screens =
        service.getProjectsAndScreens("public_,_public", "Screens");
    assertEquals(104, screens.size());
    ScreenRSpaceView aScreen =
        (ScreenRSpaceView) getTargetWithMatchingName(screens, "idr0001-graml-sysgro/screenA");
    assertEquals("idr0001-graml-sysgro/screenA", aScreen.getName());
    assertTrue(aScreen.getChildCounts() > 0);
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
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListDatasetsForProject() {
    List<DataSetRSpaceView> datasets = service.getDataSets("public_,_public", 51L);
    DataSetRSpaceView aDataSet =
        (DataSetRSpaceView) getTargetWithMatchingName(datasets, "CDK5RAP2-C");
    assertEquals("CDK5RAP2-C", aDataSet.getName());
    assertEquals(51L, aDataSet.getParentId().longValue());
    assertTrue(aDataSet.getChildCounts() > 0);
    assertEquals("", aDataSet.getDescription());
    assertEquals(51L, aDataSet.getId().longValue());
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListPlatesForScreen() {
    List<PlateRSpaceView> plates = service.getPlates("public_,_public", 51L);
    PlateRSpaceView aPlate = (PlateRSpaceView) getTargetWithMatchingName(plates, "DTT p1");
    assertEquals("DTT p1", aPlate.getName());
    assertEquals(51L, aPlate.getParentId().longValue());
    assertTrue(aPlate.getChildCounts() > 0);
    assertEquals("", aPlate.getDescription());
    assertEquals(101L, aPlate.getId().longValue());
    assertEquals(1, aPlate.getChildCounts());
    assertEquals(51L, aPlate.getParentId().longValue());
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testGetPlateAcquisitions() {

    List<PlateAcquisitionRSpaceView> plateAcquisitions =
        service.getPlateAcquisitions("public_,_public", 422L);
    PlateAcquisitionRSpaceView aPlateAcquisition =
        (PlateAcquisitionRSpaceView) getTargetWithMatchingName(plateAcquisitions, "Run 422");
    assertEquals("Run 422", aPlateAcquisition.getName());
    assertEquals("", aPlateAcquisition.getDescription());
    assertEquals(1, aPlateAcquisition.getSamplesUrls().size());
    assertEquals(
        "https://idr.openmicroscopy.org/api/v0/m/plateacquisitions/422/wellsampleindex/0/wells/",
        aPlateAcquisition.getSamplesUrls().get(0));
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
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testGetAnnotations() {
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
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListImagesForDataset() {
    List<ImageRSpaceView> images = service.getImages("public_,_public", 51L, false);
    ImageRSpaceView anImage =
        (ImageRSpaceView)
            getTargetWithMatchingName(images, "Centrin_PCNT_Cep215_20110506_Fri-1545_0_SIR_PRJ.dv");
    assertEquals("Centrin_PCNT_Cep215_20110506_Fri-1545_0_SIR_PRJ.dv", anImage.getName());
    assertEquals(
        "data:image/jpeg;base64,/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAUDBAQEAwUEBAQFBQUGBwwIBwcHBw8KCwkMEQ8SEhEPERATFhwXExQaFRARGCEYGhwdHx8fExciJCIeJBweHx7/2wBDAQUFBQcGBw4ICA4eFBEUHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh7/wAARCABgAGADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD4yooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAoopwbCFdoOcc9xQNDaKKKBBRRRQAUUUUAFFFWry0FtGCZ43k3AMqnOAVDA56dyPwpOSTsaQpTnFzS0W5VoooHWmZhRUlxC8LlWVgM/KSpGR1B59iD+NR0k76obVnZhRRRTEFFFFABRRRQAVIGjEQ+VvNDdexGKjopNCauFAoopjLmp30l9M0sjzMWYuQ77uSADjgeg/IVTooqYxUVZCSsFFFFUMKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP/Z",
        anImage.getBase64ThumbnailData());
    assertEquals(1884807L, anImage.getId().longValue());
    assertEquals(51L, anImage.getParentId().longValue());
    assertEquals(33, images.size());
    assertTrue(anImage.getDisplayImageData().contains("Z-sections = 1"));
    assertTrue(anImage.getDisplayImageData().contains("Timepoints = 1"));
    assertTrue(anImage.getDisplayImageData().contains("Number of Channels = 3"));
    assertTrue(anImage.getDisplayImageData().contains("Pixels Type = float"));
    assertTrue(anImage.getDisplayImageData().contains("Dimensions(XY) = 256 x 256"));
    assertTrue(
        anImage
            .getDisplayImageData()
            .contains("Pixel Size (XYZ) = 0.040 µm x 0.040 µm x 0.125 µm"));
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testGetImage() {
    ImageRSpaceView anImage = service.getImage("public_,_public", 1884838L, 51L, false);
    assertEquals("siControl_N20_Cep215_I_20110411_Mon-1503_0_SIR_PRJ.dv", anImage.getName());
    assertEquals(1884838L, anImage.getId().longValue());
    assertEquals(51L, anImage.getParentId().longValue());
    assertTrue(anImage.getDisplayImageData().contains("Z-sections = 1"));
    assertTrue(anImage.getDisplayImageData().contains("Timepoints = 1"));
    assertTrue(anImage.getDisplayImageData().contains("Number of Channels = 2"));
    assertTrue(anImage.getDisplayImageData().contains("Pixels Type = float"));
    assertTrue(anImage.getDisplayImageData().contains("Dimensions(XY) = 256 x 256"));
    assertTrue(
        anImage
            .getDisplayImageData()
            .contains("Pixel Size (XYZ) = 0.040 µm x 0.040 µm x 0.125 µm"));
    assertTrue(
        anImage
            .getDisplayImageData()
            .contains(
                "Channels = [name = PCNT colour = 16711935 photo interpretation = Monochrome] [name"
                    + " = CDK5RAP2-C colour = -16776961 photo interpretation = Monochrome] "));
    assertTrue(
        "unexpected image data: " + anImage.getBase64ThumbnailData(),
        anImage.getBase64ThumbnailData().startsWith("data:image/jpeg;base64,/9j/4AAQSkZJRgABAgA"));
  }

  @SneakyThrows
  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testGetWells() {
    List<WellRSpaceView> wells = service.getWells("public_,_public", 422L, 422L, false, 0);
    WellRSpaceView aWell =
        (WellRSpaceView)
            getTargetWithMatchingName(
                wells,
                "plate1_1_013 [Well 1, Field 1 (Spot 1)]",
                (well) -> well.getChildren().get(0).getChildren().get(0));
    assertEquals("0", aWell.getColumn());
    assertEquals("0", aWell.getRow());
    assertEquals(67070L, aWell.getId().longValue());
    assertEquals(422L, aWell.getParentId().longValue());
    assertEquals(96, wells.size());
    WellSampleDataRSpaceView child = (WellSampleDataRSpaceView) aWell.getChildren().get(0);
    assertEquals(165988, child.getId().longValue());
    assertEquals(422L, child.getParentId().longValue());
    ImageRSpaceView image = (ImageRSpaceView) child.getChildren().get(0);
    assertEquals("plate1_1_013 [Well 1, Field 1 (Spot 1)]", image.getName());
    assertEquals(179693L, image.getId().longValue());
    assertEquals(422L, image.getParentId().longValue());
    assertEquals(0, image.getDisplayImageData().size());
    assertEquals(
        "data:image/jpeg;base64,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",
        image.getBase64ThumbnailData());
  }
}
