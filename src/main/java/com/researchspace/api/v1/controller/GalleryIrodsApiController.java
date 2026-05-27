package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GalleryIrodsApi;
import com.researchspace.api.v1.model.ApiConfiguredLocation;
import com.researchspace.api.v1.model.ApiExternalStorageInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.ApiGalleryFilestoreOperationRequest;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FilestoreWriteManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RecordDeletionManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.validation.Valid;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@NoArgsConstructor
@ApiController
public class GalleryIrodsApiController extends GalleryFilestoresBaseApiController
    implements GalleryIrodsApi {

  @Autowired private RecordDeletionManager deletionManager;

  @Autowired @Setter private FilestoreWriteManager filestoreWriteManager;

  protected UriComponentsBuilder irodsGalleryBaseLink;

  protected enum Operation {
    copy,
    move
  }

  protected GalleryIrodsApiController(
      NfsManager nfsManager,
      RecordDeletionManager deletionManager,
      GalleryFilestoresCredentialsStore credentialsStore,
      IPropertyHolder propertyHolder) {
    this.nfsManager = nfsManager;
    this.deletionManager = deletionManager;
    this.credentialsStore = credentialsStore;
    this.properties = propertyHolder;
  }

  @PostConstruct
  private void init() {
    irodsGalleryBaseLink =
        UriComponentsBuilder.fromHttpUrl(getServerURL()).path(API_V1_GALLERY_IRODS);
  }

  @Override
  public ApiExternalStorageInfo getExternalLocationsInfo(
      @RequestParam(value = PARAM_RECORD_IDS, required = false) List<Long> recordIds,
      @RequestAttribute(name = "user") User user) {

    assertFilestoresApiEnabled(user);
    List<NfsFileStoreInfo> iRodsFileStoreInfos =
        nfsManager.getFileStoreInfosForUser(user).stream()
            .filter(fs -> NfsClientType.IRODS.toString().equals(fs.getFileSystem().getClientType()))
            .collect(Collectors.toList());

    NfsFileSystem iRodsFileSystem = null;
    if (!iRodsFileStoreInfos.isEmpty()) {
      iRodsFileSystem =
          nfsManager.getFileSystem(iRodsFileStoreInfos.get(0).getFileSystem().getId());
    }

    // getting only the file stores bound to the current file system
    iRodsFileStoreInfos = filterNfsFileStoresByFilesystem(iRodsFileStoreInfos, iRodsFileSystem);

    // build Configured Locations
    Set<ApiConfiguredLocation> configuredLocations = new LinkedHashSet<>();
    for (NfsFileStoreInfo currentFileStore : iRodsFileStoreInfos) {
      ApiConfiguredLocation location = buildCurrentLocation(currentFileStore);
      addOperationLinks(location, recordIds, currentFileStore);
      configuredLocations.add(location);
    }

    return buildResponseWithSelfLink(recordIds, iRodsFileSystem, configuredLocations);
  }

  @Override
  public ApiExternalStorageOperationResult copyToIRODS(
      @RequestParam(value = PARAM_RECORD_IDS, required = false) Set<Long> recordIds,
      @RequestParam(value = PARAM_FILESTORE_PATH_ID) Long filestorePathId,
      @RequestBody @Valid ApiNfsCredentials credentials,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    log.info("Begin copying files to IRODS");
    ApiExternalStorageOperationResult result =
        performCopyToIRODS(recordIds, filestorePathId, credentials, errors, user);
    log.info("End copying files to IRODS");
    result.buildAndAddSelfLink(
        "/copy",
        "",
        Map.of(
            PARAM_RECORD_IDS, StringUtils.join(recordIds, ','),
            PARAM_FILESTORE_PATH_ID, filestorePathId.toString()),
        irodsGalleryBaseLink);
    return result;
  }

  @Override
  public ApiExternalStorageOperationResult moveToIRODS(
      @RequestParam(value = PARAM_RECORD_IDS, required = false) Set<Long> recordIds,
      @RequestParam(value = PARAM_FILESTORE_PATH_ID) Long filestorePathId,
      @RequestBody @Valid ApiNfsCredentials credentials,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertFilestoresApiEnabled(user);
    log.info("Begin moving files to IRODS");
    ApiExternalStorageOperationResult result =
        performMoveToIRODS(recordIds, filestorePathId, credentials, errors, user);
    log.info("End moving files to IRODS");
    result.buildAndAddSelfLink(
        "/move",
        "",
        Map.of(
            PARAM_RECORD_IDS,
            StringUtils.join(recordIds, ','),
            PARAM_FILESTORE_PATH_ID,
            filestorePathId.toString()),
        irodsGalleryBaseLink);
    return result;
  }

  private ApiExternalStorageOperationResult performCopyToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException {
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(recordIds, credentials);
    return filestoreWriteManager
        .uploadToFilestore(filestorePathId, request, errors, user)
        .getOperationResult();
  }

  private ApiExternalStorageOperationResult performMoveToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException {
    ApiGalleryFilestoreOperationRequest request =
        new ApiGalleryFilestoreOperationRequest(recordIds, credentials);
    FilestoreWriteManager.UploadOutcome outcome =
        filestoreWriteManager.uploadToFilestore(filestorePathId, request, errors, user);

    ApiExternalStorageOperationResult moveResult;
    try {
      moveResult =
          ApiExternalStorageOperationResult.of(
              deletionManager.deleteMediaFileSet(outcome.getSucceededMediaFiles(), user));
    } catch (AuthorizationException | ObjectRetrievalFailureException e) {
      log.error("Error deleting media files from RSpace: ", e);
      errors.addError(new ObjectError("mediaFile", e.getMessage()));
      throwBindExceptionIfErrors(errors);
      return outcome.getOperationResult();
    }
    moveResult.addAll(outcome.getOperationResult().getFailedRecords());
    return moveResult;
  }

  @NotNull
  private static String getIrodsServerAddress(NfsFileSystem iRodsFileSystem) {
    String irodsPort =
        iRodsFileSystem.getClientOptions()
            .split("IRODS_PORT=")[
            iRodsFileSystem.getClientOptions().split("IRODS_PORT=").length - 1];
    irodsPort = irodsPort.replace("\n", "").replace("\r", "");
    return new StringBuilder()
        .append("http://")
        .append(iRodsFileSystem.getUrl())
        .append(":")
        .append(irodsPort)
        .toString();
  }

  private void addOperationLinks(
      ApiConfiguredLocation location, List<Long> recordIds, NfsFileStoreInfo currentFileStore) {
    if (!CollectionUtils.isEmpty(recordIds)) {
      Map<String, String> parameters = new TreeMap<>();
      parameters.put("filestorePathId", currentFileStore.getId().toString());
      parameters.put("recordIds", StringUtils.join(recordIds, ','));
      for (Operation operation : Operation.values()) {
        String resourceLink =
            location.getResourceLink("", operation.toString(), parameters, irodsGalleryBaseLink);
        location.addLink(resourceLink, HttpMethod.POST, operation.toString());
      }
    }
  }

  @NotNull
  private ApiExternalStorageInfo buildResponseWithSelfLink(
      List<Long> recordIds,
      NfsFileSystem iRodsFileSystem,
      Set<ApiConfiguredLocation> configuredLocations) {
    ApiExternalStorageInfo response =
        ApiExternalStorageInfo.builder()
            .serverUrl(iRodsFileSystem != null ? getIrodsServerAddress(iRodsFileSystem) : "")
            .configuredLocations(configuredLocations)
            .build();
    if (CollectionUtils.isEmpty(recordIds)) {
      response.buildAndAddSelfLink("", "", irodsGalleryBaseLink);
    } else {
      response.buildAndAddSelfLink(
          "", "", Map.of(PARAM_RECORD_IDS, StringUtils.join(recordIds, ',')), irodsGalleryBaseLink);
    }
    return response;
  }

  @NotNull
  private static ApiConfiguredLocation buildCurrentLocation(NfsFileStoreInfo currentFileStore) {
    ApiConfiguredLocation location =
        ApiConfiguredLocation.builder()
            .id(currentFileStore.getId())
            .name(currentFileStore.getName())
            .path(currentFileStore.getPath())
            .build();
    return location;
  }

  @NotNull
  private static List<NfsFileStoreInfo> filterNfsFileStoresByFilesystem(
      List<NfsFileStoreInfo> iRodsFileStores, NfsFileSystem iRodsFileSystem) {

    List<NfsFileStoreInfo> filteredStores =
        iRodsFileStores.stream()
            .filter(fstore -> iRodsFileSystem.getId().equals(fstore.getFileSystem().getId()))
            .collect(Collectors.toList());
    return filteredStores;
  }
}
