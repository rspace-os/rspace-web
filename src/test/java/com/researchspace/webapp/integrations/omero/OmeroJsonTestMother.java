package com.researchspace.webapp.integrations.omero;

import static java.nio.file.Files.readString;

import java.nio.file.Path;
import lombok.SneakyThrows;

public class OmeroJsonTestMother {
  public static String versionJsonResource = "TestResources/omero_json_responses/version.json";
  public static String versionJson = makeDataJson(versionJsonResource);
  public static String urlsJsonResource = "TestResources/omero_json_responses/urls.json";
  public static String urlsJson = makeDataJson(urlsJsonResource);
  public static String serversJsonResource = "TestResources/omero_json_responses/servers.json";
  public static String serversJson = makeDataJson(serversJsonResource);
  public static String tokenJsonResource = "TestResources/omero_json_responses/token.json";
  public static String tokenJson = makeDataJson(tokenJsonResource);
  public static String fakeLoginResponseJsonResource =
      "TestResources/omero_json_responses/fake_login_response.json";
  public static String fakeLoginResponseJson = makeDataJson(fakeLoginResponseJsonResource);
  public static String projectsJsonResource = "TestResources/omero_json_responses/projects.json";
  public static String projectsJson = makeDataJson(projectsJsonResource);
  public static String projectJsonResource = "TestResources/omero_json_responses/project.json";
  public static String projectJson = makeDataJson(projectJsonResource);
  public static String imagesThumbNailsJsonResource =
      "TestResources/omero_json_responses/image_thumbnails.json";
  public static String imagesThumbNailsJson = makeDataJson(imagesThumbNailsJsonResource);

  public static String imagesThumbNailsRun422Part1JsonResource =
      "TestResources/omero_json_responses/image_thumbnails_run422_part1.json";
  public static String imagesThumbNailsRun422Part1Json =
      makeDataJson(imagesThumbNailsRun422Part1JsonResource);

  public static String imagesThumbNailsRun422Part2JsonResource =
      "TestResources/omero_json_responses/image_thumbnails_run422_part2.json";
  public static String imagesThumbNailsRun422Part2Json =
      makeDataJson(imagesThumbNailsRun422Part2JsonResource);

  public static String largeData1JsonResource =
      "TestResources/omero_json_responses/large_data_1.json";
  public static String large_data_1_json = makeDataJson(largeData1JsonResource);
  public static String largeData2JsonResource =
      "TestResources/omero_json_responses/large_data_2.json";
  public static String large_data_2_json = makeDataJson(largeData2JsonResource);
  public static String largeData3JsonResource =
      "TestResources/omero_json_responses/large_data_3.json";
  public static String large_data_3_json = makeDataJson(largeData3JsonResource);
  public static String screenAnnotationsJsonJsonResource =
      "TestResources/omero_json_responses/screen_annotations.json";
  public static String screenAnnotationsJson = makeDataJson(screenAnnotationsJsonJsonResource);
  public static String platesForScreenJsonResource =
      "TestResources/omero_json_responses/plates_for_screen.json";
  public static String platesForScreenJson = makeDataJson(platesForScreenJsonResource);
  public static String plateJsonResource = "TestResources/omero_json_responses/plate.json";
  public static String plateJson = makeDataJson(plateJsonResource);
  public static String datasetForProjectJsonResource =
      "TestResources/omero_json_responses/datasets_for_project.json";
  public static String datasetsForProjectJson = makeDataJson(datasetForProjectJsonResource);
  public static String datasetJsonResource = "TestResources/omero_json_responses/dataset.json";
  public static String datasetJson = makeDataJson(datasetJsonResource);
  public static String screenJsonResource = "TestResources/omero_json_responses/screen.json";
  public static String screenJson = makeDataJson(screenJsonResource);
  public static String imagesForDatasetJsonResource =
      "TestResources/omero_json_responses/images_for_dataset.json";
  public static String imagesForDatasetJson = makeDataJson(imagesForDatasetJsonResource);
  public static String singleImageJsonResource =
      "TestResources/omero_json_responses/single_image.json";
  public static String singleImageJson = makeDataJson(singleImageJsonResource);
  public static String plateAcquisitionsJsonResource =
      "TestResources/omero_json_responses/plate_422_acquisitions.json";
  public static String plateAcquisitionsJson = makeDataJson(plateAcquisitionsJsonResource);

  public static String screensJsonResource = "TestResources/omero_json_responses/screens.json";
  public static String screensJson = makeDataJson(screensJsonResource);

  @SneakyThrows
  private static String makeDataJson(String resource) {
    ClassLoader classLoader = OmeroJsonTestMother.class.getClassLoader();
    String versionPath = classLoader.getResource(resource).getPath();
    return readString(Path.of(versionPath)).trim();
  }
}
