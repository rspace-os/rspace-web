package com.researchspace.webapp.integrations.wopi;

import static com.researchspace.model.record.BaseRecord.DEFAULT_VARCHAR_LENGTH;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MediaFileLockHandler;
import com.researchspace.service.MediaManager;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.controller.StructuredDocumentController;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Class handling WOPI protocol endpoints for MS Office Online integration */
@Controller
@RequestMapping("/wopi")
public class WopiController extends BaseController {

  protected static final String ACCESS_TOKEN_PARAM_NAME = "access_token";

  protected static final String X_WOPI_OVERRIDE_HEADER = "X-WOPI-Override";
  protected static final String X_WOPI_LOCK_HEADER = "X-WOPI-Lock";
  protected static final String X_WOPI_OLD_LOCK_HEADER = "X-WOPI-OldLock";
  protected static final String X_WOPI_ITEMVERSION_HEADER = "X-WOPI-ItemVersion";
  protected static final String X_WOPI_SUGGESTEDTARGET_HEADER = "X-WOPI-SuggestedTarget";
  protected static final String X_WOPI_RELATIVETARGET_HEADER = "X-WOPI-RelativeTarget";
  protected static final String X_WOPI_FILECONVERSION_HEADER = "X-WOPI-FileConversion";
  protected static final String X_COOL_WOPI_IS_EXIT_SAVE = "X-COOL-WOPI-IsExitSave";

  protected static final String OVERRIDE_HEADER_LOCK = "LOCK";
  protected static final String OVERRIDE_HEADER_GET_LOCK = "GET_LOCK";
  protected static final String OVERRIDE_HEADER_REFRESH_LOCK = "REFRESH_LOCK";
  protected static final String OVERRIDE_HEADER_UNLOCK = "UNLOCK";
  protected static final String OVERRIDE_HEADER_PUT = "PUT";
  protected static final String OVERRIDE_HEADER_PUT_RELATIVE = "PUT_RELATIVE";
  protected static final String OVERRIDE_HEADER_DELETE = "DELETE";

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Autowired protected MediaManager mediaManager;

  @Autowired private IPropertyHolder propertyHolder;

  @Autowired private WopiUtils wopiUtils;

  @Autowired private StructuredDocumentController sdocController;

  @Autowired private OfficeOnlineController officeController;

  private MediaFileLockHandler mediaManagerLockHandler;

  @PostConstruct
  public void postConstructSetup() {
    mediaManagerLockHandler = mediaManager.getLockHandler();
  }

  @GetMapping("/files/{fileId}")
  @ResponseBody
  public Map<String, Object> checkFileInfo(
      @PathVariable String fileId, @RequestAttribute(name = "user") User user) {

    Validate.notNull(user, "user attribute is expected to be set by authorization interceptor");

    log.info("returning file info for {} file", fileId);
    EcatMediaFile mediaRecord = getMediaRecordFromFileId(fileId, user);
    Map<String, Object> result = new HashMap<>();

    // required properties
    result.put("BaseFileName", mediaRecord.getName());
    result.put("OwnerId", "" + mediaRecord.getOwner().getId());
    result.put("Size", mediaRecord.getSize());
    result.put("UserId", "" + user.getId());
    result.put("Version", "" + mediaRecord.getVersion());
    result.put("LastModifiedTime", getIsoDate(mediaRecord.getModificationDateAsDate()));

    // host capabilities
    result.put("SupportsLocks", true);
    result.put("SupportsGetLock", true);
    result.put("SupportsExtendedLockLength", true);
    result.put("SupportsUpdate", true);
    result.put("SupportsDeleteFile", true);

    // PostMessage properties
    result.put("PostMessageOrigin", propertyHolder.getServerUrl());
    result.put("EditModePostMessage", true);

    // user metadata properties
    result.put("UserFriendlyName", user.getDisplayName());

    // user permissions properties
    boolean isEditable = isRecordAccessPermitted(user, mediaRecord, PermissionType.WRITE);
    result.put("UserCanWrite", isEditable);
    result.put("UserCanRename", isEditable);

    // write relative ('save as') only allowed for conversion flow
    boolean canConvert =
        isEditable
            && officeController.isConversionSupportedForExtension(mediaRecord.getExtension());
    result.put("UserCanNotWriteRelative", !canConvert);

    boolean isBusinessUser = officeController.isBusinessUser(user);
    result.put("LicenseCheckForEditIsEnabled", isBusinessUser);

    // file URL properties
    result.put("DownloadUrl", propertyHolder.getServerUrl() + "/globalId/" + fileId);
    result.put("HostEditUrl", wopiUtils.getUrlToOfficeOnlineViewPage(mediaRecord, "edit"));
    result.put("SignoutUrl", propertyHolder.getServerUrl() + "/logout");

    return result;
  }

  @GetMapping("/files/{fileId}/contents")
  public void getFile(
      @PathVariable String fileId,
      @RequestAttribute(name = "user") User user,
      HttpServletRequest req,
      HttpServletResponse resp)
      throws IOException {

    Validate.notNull(user, "user attribute is expected to be set by authorization interceptor");

    EcatMediaFile mediaRecord = getMediaRecordFromFileId(fileId, user);
    resp.addHeader(X_WOPI_ITEMVERSION_HEADER, "" + mediaRecord.getVersion());

    FileProperty fileProp = mediaRecord.getFileProperty();
    Optional<FileInputStream> fisOpt = fileStore.retrieve(fileProp);
    if (fisOpt.isPresent()) {
      log.info("streaming content of {} file", fileId);
      try (FileInputStream is = fisOpt.get();
          ServletOutputStream out = resp.getOutputStream()) {
        IOUtils.copy(is, out);
      }
    } else {
      log.error("Could not retrieve fileproperty id={}", fileProp.getId());
    }
  }

  @PostMapping("/files/{fileId}")
  @ResponseBody
  public Map<String, Object> postFileOperations(
      @PathVariable String fileId,
      @RequestAttribute(name = "user") User user,
      HttpServletRequest req,
      HttpServletResponse resp)
      throws IOException {

    Validate.notNull(user, "user attribute is expected to be set by authorization interceptor");

    String overrideHeader = req.getHeader(X_WOPI_OVERRIDE_HEADER);
    String lockHeader = req.getHeader(X_WOPI_LOCK_HEADER);
    String oldLockHeader = req.getHeader(X_WOPI_OLD_LOCK_HEADER);

    // PUT_RELATIVE handled separately
    if (OVERRIDE_HEADER_PUT_RELATIVE.equals(overrideHeader)) {
      return handlePutRelativeFile(fileId, user, req, resp);
    }

    // DELETE operation handled separately, before lock check
    if (OVERRIDE_HEADER_DELETE.equals(overrideHeader)) {
      handleFileDelete(fileId, resp);
      return null;
    }

    // determine which operation is requested
    boolean isGetLockOperation = OVERRIDE_HEADER_GET_LOCK.equals(overrideHeader);

    boolean overrideHeaderValid =
        OVERRIDE_HEADER_LOCK.equals(overrideHeader)
            || OVERRIDE_HEADER_GET_LOCK.equals(overrideHeader)
            || OVERRIDE_HEADER_REFRESH_LOCK.equals(overrideHeader)
            || OVERRIDE_HEADER_UNLOCK.equals(overrideHeader);

    boolean lockHeaderEmpty = StringUtils.isEmpty(lockHeader);
    if (!overrideHeaderValid || (lockHeaderEmpty && !isGetLockOperation)) {
      if (!overrideHeaderValid) {
        log.warn("unrecognized X-WOPI-Override header value: {}", overrideHeader);
      }
      setResponseForClientError(resp);
      return Collections.emptyMap();
    }

    // lockId returned by the operation
    String lockHandlerResult = null;

    switch (overrideHeader) {
      case OVERRIDE_HEADER_LOCK:
        boolean oldLockHeaderEmpty = StringUtils.isEmpty(oldLockHeader);
        if (oldLockHeaderEmpty) {
          log.info("locking file {} with lock {}", fileId, lockHeader);
          lockHandlerResult = mediaManagerLockHandler.lock(fileId, lockHeader);
        } else {
          log.info("re-locking file {} from lock {} to lock {}", fileId, oldLockHeader, lockHeader);
          lockHandlerResult =
              mediaManagerLockHandler.unlockAndRelock(fileId, oldLockHeader, lockHeader);
        }
        break;
      case OVERRIDE_HEADER_GET_LOCK:
        log.info("retrieving lock for file {}", fileId);
        lockHandlerResult = mediaManagerLockHandler.getLock(fileId);
        break;
      case OVERRIDE_HEADER_REFRESH_LOCK:
        log.info("refreshing lock {} for file {}", lockHeader, fileId);
        lockHandlerResult = mediaManagerLockHandler.refreshLock(fileId, lockHeader);
        break;
      case OVERRIDE_HEADER_UNLOCK:
        log.info("unlocking file {} with lock {}", fileId, lockHeader);
        lockHandlerResult = mediaManagerLockHandler.unlock(fileId, lockHeader);
        break;
    }

    // handle lock handler result
    if (lockHandlerResult != null) {
      resp.addHeader(X_WOPI_LOCK_HEADER, lockHandlerResult);
      // non-null result means 'lock mismatch', unless it's GET_LOCK operation
      if (!isGetLockOperation) {
        setResponseForLockMismatch(resp);
      }
    }

    // lock/unlock operations should set version info
    if (OVERRIDE_HEADER_LOCK.equals(overrideHeader)
        || OVERRIDE_HEADER_UNLOCK.equals(overrideHeader)) {
      EcatMediaFile mediaRecord = getMediaRecordFromFileId(fileId, user);
      resp.addHeader(X_WOPI_ITEMVERSION_HEADER, "" + mediaRecord.getVersion());
    }
    return Collections.emptyMap();
  }

  private Charset utf7Charset;

  private String convertUtf7EncodedString(String utf7String) {
    if (!StringUtils.isBlank(utf7String)) {
      /* due to issue with charset registering in java we need to load Charset explicitly from dependency jar,
       * rather than rely on it being loaded from classpath.
       * see: https://stackoverflow.com/questions/39641604/spring-boot-1-4-x-and-custom-charsetprovider */
      if (utf7Charset == null) {
        utf7Charset = new com.beetstra.jutf7.CharsetProvider().charsetForName("UTF-7");
      }

      String convertedString = new String(utf7String.getBytes(), utf7Charset);
      return convertedString;
    }
    return utf7String;
  }

  private Map<String, Object> handlePutRelativeFile(
      String fileId, User user, HttpServletRequest req, HttpServletResponse resp) {

    String suggestedTargetHeader =
        convertUtf7EncodedString(req.getHeader(X_WOPI_SUGGESTEDTARGET_HEADER));
    String relativeTargetHeader =
        convertUtf7EncodedString(req.getHeader(X_WOPI_RELATIVETARGET_HEADER));

    boolean isFileConversion = req.getHeader(X_WOPI_FILECONVERSION_HEADER) != null;
    log.info("put relative file called for {}, file conversion flow: {}", fileId, isFileConversion);

    if (!isFileConversion) {
      /* due to incompatible requirements of 'save as' operation with
       * RSpace constraints we are only going to support conversion flow */
      setResponseForUnsupportedOperation(resp);
      return Collections.emptyMap();
    }

    boolean specificMode;
    if (relativeTargetHeader != null && suggestedTargetHeader == null) {
      specificMode = true;
    } else if (relativeTargetHeader == null && suggestedTargetHeader != null) {
      specificMode = false;
    } else {
      log.warn(
          "couldn't determine the requested mode, relative: {}, suggested: {}",
          relativeTargetHeader,
          suggestedTargetHeader);
      setResponseForClientError(resp);
      return Collections.emptyMap();
    }

    EcatMediaFile mediaFileToUpdate = getMediaRecordFromFileId(fileId, user);

    String proposedName;
    if (specificMode) {
      //

      /* Specific mode: WOPI client expects the host to use the file name provided exactly
       *
       * 1. A file matching the target name might be locked, and in specific mode, the host must respond with
       * a 409 Conflict and include a X-WOPI-Lock response header as described below.
       * ... but in RSpace user can have multiple files with the same name (name is not an identifier),
       * so we don't need to ever return 409.
       *
       * 2. If specified filename is illegal (e.g. too long / wrong chars) we should return 400.
       */
      String validationError = validateNewRecordName(relativeTargetHeader);
      if (validationError != null) {
        setResponseForClientError(resp);
        return Collections.emptyMap();
      }
      proposedName = relativeTargetHeader;

    } else {
      // suggested mode: the host should adjust the file name in order to make the request succeed
      proposedName =
          generateNameForPutRelativeSuggestedMode(suggestedTargetHeader, mediaFileToUpdate);
    }

    EcatMediaFile resultMediaFile = null;
    try {
      ServletInputStream inputStream = req.getInputStream();
      resultMediaFile =
          mediaManager.updateMediaFile(
              mediaFileToUpdate.getId(), inputStream, proposedName, user, null);
      log.info("saved new version of gallery file {}", resultMediaFile.getId());

    } catch (IOException ioe) {
      log.warn("couldn't save incoming input stream", ioe);
      resp.setStatus(500);
      return Collections.emptyMap();
    }

    resp.addHeader(X_WOPI_ITEMVERSION_HEADER, "" + resultMediaFile.getVersion());

    Map<String, Object> responseProps = new HashMap<>();
    responseProps.put("Name", resultMediaFile.getName());

    String currentAccessToken = req.getParameter(ACCESS_TOKEN_PARAM_NAME);
    String newAccessToken =
        accessTokenHandler
            .createAccessTokenWithOldTokenExpiryDate(
                user, resultMediaFile.getGlobalIdentifier(), currentAccessToken)
            .getAccessToken();
    responseProps.put(
        "Url",
        wopiUtils.getWopiServerFilesUrl(resultMediaFile) + "?access_token=" + newAccessToken);

    responseProps.put(
        "HostViewUrl", wopiUtils.getUrlToOfficeOnlineViewPage(resultMediaFile, "view"));
    responseProps.put(
        "HostEditUrl", wopiUtils.getUrlToOfficeOnlineViewPage(resultMediaFile, "edit"));

    return responseProps;
  }

  protected String generateNameForPutRelativeSuggestedMode(
      String suggestedTargetHeader, EcatMediaFile currentMediaFile) {
    String proposedName;
    if (suggestedTargetHeader.startsWith(".")) {
      // if suggestedTargetHeader begins with a dot (.) then it means a file extension, and original
      // name should be used
      String currentName = currentMediaFile.getName();
      proposedName = currentName.substring(0, currentName.lastIndexOf(".")) + suggestedTargetHeader;
    } else {
      // use the provided name
      proposedName = suggestedTargetHeader;
    }

    if (proposedName.length() > BaseRecord.DEFAULT_VARCHAR_LENGTH) {
      String extension = FilenameUtils.getExtension(proposedName);
      proposedName =
          StringUtils.abbreviate(
              proposedName, BaseRecord.DEFAULT_VARCHAR_LENGTH - extension.length() - 1);
      proposedName += "." + extension;
    }

    return proposedName;
  }

  private void handleFileDelete(String fileId, HttpServletResponse resp) throws IOException {

    log.info("delete file called for {}", fileId);

    // only proceed if file is unlocked
    String currentLock = mediaManagerLockHandler.getLock(fileId);
    if (!StringUtils.isEmpty(currentLock)) {
      resp.addHeader(X_WOPI_LOCK_HEADER, currentLock);
      setResponseForLockMismatch(resp);
      return;
    }

    // not supported for now
    setResponseForUnsupportedOperation(resp);
  }

  /**
   * @return error message or null if new record name is valid
   */
  private String validateNewRecordName(String newName) {
    if (!StringUtils.isBlank(newName) && newName.length() > DEFAULT_VARCHAR_LENGTH) {
      return getText("errors.maxlength", new String[] {"name", DEFAULT_VARCHAR_LENGTH + ""});
    }
    return sdocController.validateNewRecordName(newName);
  }

  @PostMapping("/files/{fileId}/contents")
  @ResponseBody
  public void putFileOperations(
      @PathVariable String fileId,
      @RequestAttribute(name = "user") User user,
      HttpServletRequest req,
      HttpServletResponse resp)
      throws IOException {

    Validate.notNull(user, "user attribute is expected to be set by authorization interceptor");

    EcatMediaFile mediaFile = getMediaRecordFromFileId(fileId, user);
    String overrideHeader = req.getHeader(X_WOPI_OVERRIDE_HEADER);
    String lockHeader = req.getHeader(X_WOPI_LOCK_HEADER);
    String isExitSave = req.getHeader(X_COOL_WOPI_IS_EXIT_SAVE);

    // required PUT header
    if (!OVERRIDE_HEADER_PUT.equals(overrideHeader)) {
      setResponseForClientError(resp);
      return;
    }

    /*
     * "When a host receives a PutFile request on a file that is not locked,
     * the host must check the current size of the file. If it is 0 bytes,
     * the PutFile request should be considered valid and should proceed.
     *
     * If it is any value other than 0 bytes, or is missing altogether,
     * the host should respond with a 409 Conflict.
     *
     * If the file is currently locked and the X-WOPI-Lock value does not match
     * the lock currently on the file the host must return a “lock mismatch” response
     * (409 Conflict) and include an X-WOPI-Lock response header."
     */
    String currentLock = mediaManagerLockHandler.getLock(fileId);
    if (StringUtils.isEmpty(currentLock) && mediaFile.getSize() > 0
        || !StringUtils.isEmpty(currentLock) && !currentLock.equals(lockHeader)) {
      /* RSPAC-2465 Collabora may have called unlock before upload of put file request completed
       *  so we bypass lock checking here if isExitSave header is preset
       */
      if (isExitSave == null || isExitSave.equals("false")) {
        log.info("mismatched lock, current: {}, provided: {}", currentLock, lockHeader);
        resp.addHeader(X_WOPI_LOCK_HEADER, currentLock);
        setResponseForLockMismatch(resp);
        return;
      }
    }
    try {
      ServletInputStream inputStream = req.getInputStream();
      EcatMediaFile updatedMedia =
          mediaManager.updateMediaFile(
              mediaFile.getId(), inputStream, mediaFile.getName(), user, lockHeader);
      log.info("saved new version of gallery file {}", mediaFile.getId());
      resp.addHeader(X_WOPI_ITEMVERSION_HEADER, "" + updatedMedia.getVersion());
      // Optional header for Collabora
      resp.addHeader("LastModifiedTime", getIsoDate(mediaFile.getModificationDateAsDate()));

    } catch (IOException ioe) {
      log.warn("couldn't save incoming input stream", ioe);
      resp.setStatus(500);
    }
  }

  private EcatMediaFile getMediaRecordFromFileId(String fileId, User user) {
    GlobalIdentifier globalId = new GlobalIdentifier(fileId);
    return baseRecordManager.retrieveMediaFile(user, globalId.getDbId(), null, null, null);
  }

  private String getIsoDate(Date date) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df =
        new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
    df.setTimeZone(tz);
    return df.format(date);
  }

  private void setResponseForClientError(HttpServletResponse resp) {
    log.warn("problematic request, returning 400 status");
    resp.setStatus(400);
  }

  private void setResponseForLockMismatch(HttpServletResponse resp) throws IOException {
    log.warn("mismatched lock, returning 409 status");
    resp.setStatus(409);
    // Set custom error code for collabora with 409 see:
    // https://sdk.collaboraonline.com/docs/How_to_integrate.html#further-differences-to-wopi
    if (propertyHolder.isCollaboraEnabled()) {
      resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
      String coolStatusCode = "{ \"COOLStatusCode\": 1010 }";
      PrintWriter writer = resp.getWriter();
      writer.write(coolStatusCode);
      writer.flush();
    }
  }

  private void setResponseForUnsupportedOperation(HttpServletResponse resp) {
    log.warn("request for unsupported operation, returning 501 status");
    resp.setStatus(501);
  }

  /*
   * for tests
   */
  void setLockHandler(MediaFileLockHandler lockHandler) {
    this.mediaManagerLockHandler = lockHandler;
  }
}
