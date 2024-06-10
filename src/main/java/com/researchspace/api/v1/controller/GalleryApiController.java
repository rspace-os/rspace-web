package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GalleryApi;
import com.researchspace.api.v1.model.ApiConfiguredLocation;
import com.researchspace.api.v1.model.ApiExternalStorageInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import com.researchspace.api.v1.model.NfsUserFileSystem;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.ExternalStorageLocation;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.ApiNfsCredentials;
import com.researchspace.netfiles.NfsAuthentication;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.ExternalStorageManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RecordDeletionManager;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.validation.Valid;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class GalleryApiController extends BaseApiController implements GalleryApi {

  @Autowired private NfsManager nfsManager;

  @Autowired private RecordDeletionManager deletionManager;

  @Autowired private BaseRecordManager baseRecordManager;

  @Autowired private ExternalStorageManager externalStorageManager;

  @Autowired
  @Qualifier("nfsUserPasswordAuthentication")
  private NfsAuthentication nfsAuthentication;

  protected UriComponentsBuilder rsBaseLink;
  protected Map<NfsUserFileSystem, ApiNfsCredentials> credentialsMapCache = new LinkedHashMap<>();

  protected enum Operation {
    copy,
    move
  }

  protected GalleryApiController(
      NfsManager nfsManager,
      RecordDeletionManager deletionManager,
      BaseRecordManager baseRecordManager,
      NfsAuthentication nfsAuthentication,
      ExternalStorageManager externalStorageManager) {
    this.nfsManager = nfsManager;
    this.deletionManager = deletionManager;
    this.baseRecordManager = baseRecordManager;
    this.nfsAuthentication = nfsAuthentication;
    this.externalStorageManager = externalStorageManager;
  }

  @PostConstruct
  private void init() {
    rsBaseLink = UriComponentsBuilder.fromHttpUrl(getServerURL()).path(API_GALLERY_V1);
  }

  @Override
  public ApiExternalStorageInfo getExternalLocationsInfo(
      @RequestParam(value = PARAM_RECORD_IDS, required = false) List<Long> recordIds,
      @RequestAttribute(name = PARAM_USER) User user) {

    List<NfsFileStore> iRodsFileStores =
        nfsManager.getFileStoresForUser(user.getId()).stream()
            .filter(fs -> NfsClientType.IRODS.equals(fs.getFileSystem().getClientType()))
            .collect(Collectors.toList());

    NfsFileSystem iRodsFileSystem = null;
    if (!iRodsFileStores.isEmpty()) {
      iRodsFileSystem = iRodsFileStores.get(0).getFileSystem();
    }

    // getting only the file stores bound to the current file system
    iRodsFileStores = filterNfsFileStoresByFilesystem(iRodsFileStores, iRodsFileSystem);

    // build Configured Locations
    Set<ApiConfiguredLocation> configuredLocations = new LinkedHashSet<>();
    for (NfsFileStore currentFileStore : iRodsFileStores) {
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
      @RequestAttribute(name = PARAM_USER) User user)
      throws BindException {
    log.info("Begin copying files to IRODS");
    ApiExternalStorageOperationResult result =
        performCopyToIRODS(recordIds, filestorePathId, credentials, errors, user);
    log.info("End copying files to IRODS");
    result.buildAndAddSelfLink(
        IRODS_ENDPOINT + "/copy",
        "",
        Map.of(
            PARAM_RECORD_IDS, StringUtils.join(recordIds, ','),
            PARAM_FILESTORE_PATH_ID, filestorePathId.toString()),
        rsBaseLink);
    return result;
  }

  @Override
  public ApiExternalStorageOperationResult moveToIRODS(
      @RequestParam(value = PARAM_RECORD_IDS, required = false) Set<Long> recordIds,
      @RequestParam(value = PARAM_FILESTORE_PATH_ID) Long filestorePathId,
      @RequestBody @Valid ApiNfsCredentials credentials,
      BindingResult errors,
      @RequestAttribute(name = PARAM_USER) User user)
      throws BindException {
    log.info("Begin moving files to IRODS");
    ApiExternalStorageOperationResult result =
        performMoveToIRODS(recordIds, filestorePathId, credentials, errors, user);
    log.info("End moving files to IRODS");
    result.buildAndAddSelfLink(
        IRODS_ENDPOINT + "/move",
        "",
        Map.of(
            PARAM_RECORD_IDS,
            StringUtils.join(recordIds, ','),
            PARAM_FILESTORE_PATH_ID,
            filestorePathId.toString()),
        rsBaseLink);
    return result;
  }

  private ApiExternalStorageOperationResult performCopyToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    NfsFileStore nfsFileStore = validateInputAndGetFilestore(recordIds, filestorePathId, errors);
    NfsClient nfsClient =
        validateCredentialsAndLoginNfs(credentials, errors, user, nfsFileStore.getFileSystem());

    Map<Long, EcatMediaFile> mediaFileMapById = retrieveMediaFiles(recordIds, user, errors).stream()
        .collect(Collectors.toMap(EcatMediaFile::getId, value -> value));

    log.info(
        "Preparing file list {} to be copied into IRODS path [{}]",
        mediaFileMapById.values(),
        nfsFileStore.getPath());
    try {
      // store the files to the IRODS server
      result = nfsManager.uploadFilesToNfs(mediaFileMapById.values(), nfsFileStore.getPath(), nfsClient);

      ExternalStorageLocation externalLocation;
      for (ApiExternalStorageOperationInfo resultInfo : result.getFileInfoDetails()) {
        if (resultInfo.getSucceeded()) {
          externalLocation = new ExternalStorageLocation();
          externalLocation.setFileStore(nfsFileStore);
          externalLocation.setOperationUser(user);
          externalLocation.setExternalStorageId(resultInfo.getExternalStorageId());
          externalLocation.setConnectedMediaFile(mediaFileMapById.get(resultInfo.getRecordId()));
          externalStorageManager.saveExternalStorageLocation(externalLocation);
        }
      }

    } catch (Exception e) {
      log.error("An error occurred while uploading files to IRODS: ", e);
      errors.addError(new ObjectError("nfsClient", e.getMessage()));
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    return result;
  }

  private ApiExternalStorageOperationResult performMoveToIRODS(
      Set<Long> recordIds,
      Long filestorePathId,
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user)
      throws BindException {

    ApiExternalStorageOperationResult copyResult =
        performCopyToIRODS(recordIds, filestorePathId, credentials, errors, user);
    ApiExternalStorageOperationResult movingResult = null;
    Set<EcatMediaFile> mediaFileSet =
        retrieveMediaFiles(copyResult.getSucceededRecordIds(), user, errors);
    log.info("Preparing file list {} to be removed from RSpace", mediaFileSet);
    try {
      movingResult =
          ApiExternalStorageOperationResult.of(
              deletionManager.deleteMediaFileSet(mediaFileSet, user));
    } catch (AuthorizationException | ObjectRetrievalFailureException e) {
      log.error("An error occurred while deleting files from RSpace: ", e);
      errors.addError(new ObjectError("mediaFile", e.getMessage()));
    } finally {
      throwBindExceptionIfErrors(errors);
    }
    movingResult.addAll(copyResult.getFailedRecords());
    return movingResult;
  }

  @NotNull
  private Set<EcatMediaFile> retrieveMediaFiles(
      Set<Long> recordIds, User user, BindingResult errors) throws BindException {
    Set<EcatMediaFile> filesRetrieved = new LinkedHashSet<>();
    for (Long recordId : recordIds) {
      try {
        filesRetrieved.add(baseRecordManager.retrieveMediaFile(user, recordId));
      } catch (ObjectRetrievalFailureException ex) {
        errors.addError(new ObjectError("recordIds", ex.getMessage()));
      } finally {
        throwBindExceptionIfErrors(errors);
      }
    }
    return filesRetrieved;
  }

  @NotNull
  private NfsClient validateCredentialsAndLoginNfs(
      ApiNfsCredentials credentials,
      BindingResult errors,
      User user,
      NfsFileSystem currentFileSystem)
      throws BindException {
    // add login for the filestore into the Map
    NfsUserFileSystem currentUserFilesystemPair = new NfsUserFileSystem(user, currentFileSystem);
    if (!credentialsMapCache.containsKey(currentUserFilesystemPair)
        || (credentials != null
            && StringUtils.isNotBlank(credentials.getUsername())
            && StringUtils.isNotBlank(credentials.getPassword()))) {
      credentialsMapCache.put(
          currentUserFilesystemPair,
          new ApiNfsCredentials(user, credentials.getUsername(), credentials.getPassword()));
    }

    String credentialValidationError =
        nfsAuthentication.validateCredentials(
            credentialsMapCache.get(currentUserFilesystemPair).getUsername(),
            credentialsMapCache.get(currentUserFilesystemPair).getPassword(),
            credentialsMapCache.get(currentUserFilesystemPair).getUser());

    if (credentialValidationError != null) {
      errors.addError(new ObjectError("credentials", credentialValidationError));
      throwBindExceptionIfErrors(errors);
    }

    NfsClient nfsClient =
        nfsAuthentication.login(
            credentialsMapCache.get(currentUserFilesystemPair).getUsername(),
            credentialsMapCache.get(currentUserFilesystemPair).getPassword(),
            currentFileSystem,
            user);
    if (!nfsClient.isUserLoggedIn()) {
      errors.addError(new ObjectError("nfsClient", "User is not logged in"));
      throwBindExceptionIfErrors(errors);
    }
    return nfsClient;
  }

  @NotNull
  private NfsFileStore validateInputAndGetFilestore(
      Set<Long> recordIds, Long filestorePathId, BindingResult errors) throws BindException {
    if (CollectionUtils.isEmpty(recordIds)) {
      errors.addError(new ObjectError("recordIds", PARAM_RECORD_IDS + " is mandatory"));
    }
    if (filestorePathId == null) {
      errors.addError(
          new ObjectError("filestorePathId", PARAM_FILESTORE_PATH_ID + " is mandatory"));
    }
    throwBindExceptionIfErrors(errors);
    NfsFileStore nfsFileStore = nfsManager.getNfsFileStore(filestorePathId);
    if (nfsFileStore == null) {
      errors.addError(
          new ObjectError("nfsFileStore", "Could not find file store with id: " + filestorePathId));
    }
    throwBindExceptionIfErrors(errors);
    return nfsFileStore;
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
      ApiConfiguredLocation location, List<Long> recordIds, NfsFileStore currentFileStore) {
    if (!CollectionUtils.isEmpty(recordIds)) {
      Map<String, String> parameters = new TreeMap<>();
      parameters.put("filestorePathId", currentFileStore.getId().toString());
      parameters.put("recordIds", StringUtils.join(recordIds, ','));
      for (Operation operation : Operation.values()) {
        String resourceLink =
            location.getResourceLink(IRODS_ENDPOINT, operation.toString(), parameters, rsBaseLink);
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
      response.buildAndAddSelfLink(IRODS_ENDPOINT, "", rsBaseLink);
    } else {
      response.buildAndAddSelfLink(
          IRODS_ENDPOINT,
          "",
          Map.of(PARAM_RECORD_IDS, StringUtils.join(recordIds, ',')),
          rsBaseLink);
    }
    return response;
  }

  @NotNull
  private static ApiConfiguredLocation buildCurrentLocation(NfsFileStore currentFileStore) {
    ApiConfiguredLocation location =
        ApiConfiguredLocation.builder()
            .id(currentFileStore.getId())
            .name(currentFileStore.getName())
            .path(currentFileStore.getPath())
            .build();
    return location;
  }

  @NotNull
  private static List<NfsFileStore> filterNfsFileStoresByFilesystem(
      List<NfsFileStore> iRodsFileStores, NfsFileSystem iRodsFileSystem) {
    iRodsFileStores =
        iRodsFileStores.stream()
            .filter(fstore -> iRodsFileSystem.equals(fstore.getFileSystem()))
            .collect(Collectors.toList());
    return iRodsFileStores;
  }
}
