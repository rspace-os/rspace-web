package com.researchspace.integrations.omero.client;

import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.ProjectRSpaceView;
import com.researchspace.integrations.omero.model.ScreenRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import com.researchspace.webapp.integrations.omero.JSONClient;
import com.researchspace.webapp.integrations.omero.OmeroAuthController;
import com.researchspace.webapp.integrations.omero.OmeroUser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import javax.json.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@SuppressFBWarnings("DM_DEFAULT_ENCODING")
@Slf4j
@Component
public class OmeroClientImpl implements OmeroClient {
  @Value("${omero.small.thumbnail.cache.size}")
  private int omeroSmallThumbnailCacheSize;

  @Value("${omero.medium.thumbnail.cache.size}")
  private int omeroMediumThumbnailCacheSize;

  @Value("${omero.large.thumbnail.cache.size}")
  private int omeroLargeThumbnailCacheSize;

  // Hold up to MAX_SIZE thumbnails and then evict
  private static class ThumbnailCache extends LinkedHashMap<Long, String> {
    private final int MAX_SIZE;

    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
      return size() > MAX_SIZE;
    }

    public ThumbnailCache(int size) {
      MAX_SIZE = size;
    }
  }

  @Value("${omero.api.url}")
  private String omeroApiUrl;

  @Value("${omero.servername}")
  private String omeroServerName;

  private ThumbnailCache imageThumbnailsCache = new ThumbnailCache(omeroSmallThumbnailCacheSize);
  // for large thumbnails - fetched through render_thumbnail API, about 80x sower than default
  // thumbnails.
  private ThumbnailCache largeImageThumbnailsCache =
      new ThumbnailCache(omeroLargeThumbnailCacheSize);
  private ThumbnailCache mediumImageThumbnailsCache =
      new ThumbnailCache(omeroMediumThumbnailCacheSize);

  @SneakyThrows
  @Override
  public List<? extends OmeroRSpaceView> getProjectsAndScreens(
      String credentials, String dataType) {
    JSONClient jsonClient = loginJsonClient(credentials);
    boolean skipScreens = false, skipProjects = false;
    if (("Projects").equals(dataType)) {
      skipScreens = true;
    } else if (("Screens").equals(dataType)) {
      skipProjects = true;
    }
    List<OmeroRSpaceView> projectsForView = new ArrayList<>();
    if (!skipProjects) {
      Collection<JsonObject> projects = jsonClient.listProjects();
      for (JsonObject jsonProject : projects) {
        projectsForView.add(new ProjectRSpaceView(jsonProject, jsonClient));
      }
    }
    if (!skipScreens) {
      Collection<JsonObject> screens = jsonClient.listScreens();
      for (JsonObject jsonScreen : screens) {
        projectsForView.add(new ScreenRSpaceView(jsonScreen, jsonClient));
      }
    }
    return projectsForView;
  }

  @SneakyThrows
  private Map<Long, String> getImageThumbnailsDetails(
      List<Long> imageIdsInitial, JSONClient jsonClient, boolean fetchLarge) {
    return getImageThumbnails(imageIdsInitial, jsonClient, fetchLarge, true);
  }

  @SneakyThrows
  private Map<Long, String> getImageThumbnails(
      List<Long> imageIdsInitial, JSONClient jsonClient, boolean fetchLarge) {
    return getImageThumbnails(imageIdsInitial, jsonClient, fetchLarge, false);
  }

  @SneakyThrows
  private Map<Long, String> getImageThumbnails(
      List<Long> imageIdsInitial, JSONClient jsonClient, boolean fetchLarge, boolean isDetails) {
    Map<Long, String> results = new HashMap<>();
    List<Long> imageIds = new ArrayList<>(imageIdsInitial);
    for (Long imageID : imageIds) {
      String cached =
          fetchLarge
              ? largeImageThumbnailsCache.get(imageID)
              : isDetails
                  ? mediumImageThumbnailsCache.get(imageID)
                  : imageThumbnailsCache.get(imageID);
      if (cached != null) {
        results.put(imageID, cached);
      }
    }
    if (results.size() > 0) {
      Set<Long> resultsIDs = results.keySet();
      imageIds.removeAll(resultsIDs);
    }
    List<String> params = new ArrayList<>();
    for (long imageID : imageIds) {
      params.add("id=" + imageID);
    }
    Map<Long, String> thumbNailJsonData = new HashMap<>();
    if (imageIds.size() > 0) {
      if (fetchLarge || isDetails) {
        // for large thumbnails - fetched through render_thumbnail API, about 80x sower than default
        // thumbnails.
        for (long imageID : imageIds) {
          int size = fetchLarge ? 800 : 400;
          byte[] data =
              jsonClient.getRenderedThumbnail(
                  omeroApiUrl + "/webgateway/render_thumbnail/", imageID, size);
          String dataStr = new String(Base64.getEncoder().encode(data));
          thumbNailJsonData.put(imageID, ("data:image/jpeg;base64," + dataStr));
          if (fetchLarge) {
            largeImageThumbnailsCache.putAll(thumbNailJsonData);
          } else {
            mediumImageThumbnailsCache.putAll(thumbNailJsonData);
          }
        }
      } else {
        long tStart = System.currentTimeMillis();
        List<List<String>> listOfParamBlocks = new ArrayList<>();
        List<String> blockOfParams = null;
        for (int i = 0; i < params.size(); i++) {
          if (i % 50 == 0) {
            blockOfParams = new ArrayList<>();
            listOfParamBlocks.add(blockOfParams);
          }
          blockOfParams.add(params.get(i));
        }
        AtomicReference<Map<Long, String>> thumbNailsParallelReference =
            new AtomicReference<>(thumbNailJsonData);
        listOfParamBlocks.stream()
            .parallel()
            .map(
                someParams -> {
                  try {
                    return jsonClient.getThumbnails(
                        omeroApiUrl + "webclient/get_thumbnails/", someParams);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .forEach(
                blockOfResults -> {
                  UnaryOperator<Map<Long, String>> updateFunction =
                      s -> {
                        s.putAll(blockOfResults);
                        return s;
                      };
                  thumbNailsParallelReference.getAndUpdate(updateFunction);
                });
        long tEnd = System.currentTimeMillis();
        log.debug("Get thumbnails took " + (tEnd - tStart));
        imageThumbnailsCache.putAll(thumbNailJsonData);
      }
    }
    results.putAll(thumbNailJsonData);
    return results;
  }

  @SneakyThrows
  private JSONClient loginJsonClient(String credentials) {
    OmeroUser loginData =
        OmeroAuthController.OmeroAccessTokenReader.createOmeroUserCredentialsFromDelimitedString(
            credentials);
    JSONClient jsonClient = new JSONClient(omeroApiUrl);
    Map<String, Integer> servers = jsonClient.getServers();
    jsonClient.login(
        loginData.getWebClientUserName(),
        loginData.getWebClientPassword(),
        servers.get(omeroServerName));
    return jsonClient;
  }

  @SneakyThrows
  @Override
  public List<DataSetRSpaceView> getDataSets(String cred, long projectid) {
    List<DataSetRSpaceView> datasets = new ArrayList<>();
    JSONClient jsonClient = loginJsonClient(cred);
    JsonObject jsonProject = jsonClient.getProjectWithId(projectid);
    Collection<JsonObject> datasetsJson = jsonClient.listDatasetsForProject(jsonProject);
    for (JsonObject jsonDataSet : datasetsJson) {
      datasets.add(new DataSetRSpaceView(jsonDataSet, jsonClient, projectid));
    }
    return datasets;
  }

  @SneakyThrows
  @Override
  public List<PlateRSpaceView> getPlates(String cred, long screenid) {
    List<PlateRSpaceView> plates = new ArrayList<>();
    JSONClient jsonClient = loginJsonClient(cred);
    JsonObject jsonScreen = jsonClient.getScreenWithId(screenid);
    Collection<JsonObject> platesJson = jsonClient.listPlatesForScreen(jsonScreen);
    for (JsonObject jsonDataSet : platesJson) {
      plates.add(new PlateRSpaceView(jsonDataSet, jsonClient, screenid));
    }
    return plates;
  }

  @Override
  public List<String> getAnnotations(String cred, long id, String type) {
    JSONClient jsonClient = loginJsonClient(cred);
    String rootUrl = jsonClient.getRootUrl();
    return jsonClient.getAnnotations(rootUrl, type, id);
  }

  @SneakyThrows
  @Override
  public List<ImageRSpaceView> getImages(String credentials, long datasetID, boolean fetchLarge) {
    List<ImageRSpaceView> imagesForDisplay = new ArrayList<>();
    JSONClient jsonClient = loginJsonClient(credentials);
    JsonObject jsonDataset = jsonClient.getDataSetWithId("" + datasetID);
    long getStart = System.currentTimeMillis();
    Collection<JsonObject> jsonImages = jsonClient.listImagesForDataset(jsonDataset);
    long getEnd = System.currentTimeMillis();
    log.debug("List images for dataset took: " + (getEnd - getStart));
    List<Long> imageIds = new ArrayList<>();
    if (jsonImages != null) {
      for (JsonObject imageJson : jsonImages) {
        imageIds.add(Long.valueOf(imageJson.getInt("@id")));
      }
      Map<Long, String> imageThumbnails =
          getImageThumbnails(
              imageIds, jsonClient, fetchLarge); // get thumbnails in a batch then add to cache
      for (JsonObject imageJson : jsonImages) {
        Long imageID = Long.valueOf(imageJson.getInt("@id"));
        String base64Thumbnail = imageThumbnails.get(imageID);
        ImageRSpaceView imageRSpaceView =
            new ImageRSpaceView(imageJson, datasetID, base64Thumbnail, Collections.EMPTY_LIST);
        imagesForDisplay.add(imageRSpaceView);
      }
    }
    return imagesForDisplay;
  }

  @SneakyThrows
  @Override
  public ImageRSpaceView getImage(String cred, long imageID, long dataSetID, boolean fetchLarge) {
    JSONClient jsonClient = loginJsonClient(cred);
    JsonObject imageJson = jsonClient.getSingleImage(imageID);
    Map<Long, String> imageThumbnails =
        getImageThumbnailsDetails(List.of(imageID), jsonClient, fetchLarge);
    String base64Thumbnail = imageThumbnails.get(imageID);
    ImageRSpaceView imageRSpaceView =
        new ImageRSpaceView(imageJson, dataSetID, base64Thumbnail, Collections.EMPTY_LIST);
    return imageRSpaceView;
  }

  @Override
  @SneakyThrows
  public List<PlateAcquisitionRSpaceView> getPlateAcquisitions(String credentials, long plateID) {
    List<PlateAcquisitionRSpaceView> pav = new ArrayList<>();
    JSONClient jsonClient = loginJsonClient(credentials);
    JsonObject jsonPlate = jsonClient.getPlateWithId("" + plateID);
    Collection<JsonObject> jpas = jsonClient.listAcquisitionsForPlate(jsonPlate);
    for (JsonObject jpa : jpas) {
      pav.add(new PlateAcquisitionRSpaceView(jpa, jsonPlate, plateID));
    }
    if (jpas.size() == 0) {
      pav.add(new PlateAcquisitionRSpaceView(jsonPlate, plateID));
    }
    return pav;
  }

  @Override
  @SneakyThrows
  public List<WellRSpaceView> getWells(
      String credentials,
      long plateID,
      long plateAcquisitionID,
      boolean fetchLarge,
      int wellIndex) {
    List<WellRSpaceView> wellsForDisplay = new ArrayList<>();
    JSONClient jsonClient = loginJsonClient(credentials);
    JsonObject jsonPlate = jsonClient.getPlateWithId("" + plateID);
    Collection<JsonObject> jsonWells =
        jsonClient.listWellsForPlateAcquisition(jsonPlate, plateAcquisitionID, wellIndex);
    List<Long> wellIDs = new ArrayList<>();
    List<Long> wellSampleImageIDs = new ArrayList<>();
    long start = System.currentTimeMillis();
    if (jsonWells != null) {
      for (JsonObject jsonWell : jsonWells) {
        wellIDs.add(Long.valueOf(jsonWell.getInt("@id")));
        List<JsonObject> wellSamples = WellRSpaceView.getWellSamples(jsonWell);
        for (JsonObject jws : wellSamples) {
          wellSampleImageIDs.add(Long.valueOf(jws.getJsonObject("Image").getInt("@id")));
        }
      }
      Map<Long, String> imageThumbnails =
          getImageThumbnails(
              wellSampleImageIDs,
              jsonClient,
              fetchLarge); // get thumbnails in a batch then add to cache
      for (JsonObject jsonWell : jsonWells) {
        WellRSpaceView wellRSpaceView =
            new WellRSpaceView(jsonWell, plateID, imageThumbnails, Collections.EMPTY_LIST);
        wellsForDisplay.add(wellRSpaceView);
      }
    }
    long end = System.currentTimeMillis();
    log.debug("getWells took: " + (end - start));
    return wellsForDisplay;
  }
}
