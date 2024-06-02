package com.researchspace.api.v1.controller;

import static com.researchspace.service.FolderManager.API_INBOX_LOCK;

import com.researchspace.api.v1.FilesApi;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiFileSearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MediaManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@ApiController
public class FilesApiController extends BaseApiController implements FilesApi {

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  private @Autowired FilesAPIHandler fileHandler;
  private @Autowired MediaManager mediaMgr;
  private @Autowired FolderManager folderMgr;
  private @Autowired ApiAccountInitialiser contentInit;
  private @Autowired IPermissionUtils permUtils;

  @Override
  public ApiFileSearchResult getFiles(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiFileSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    log.debug("Incoming pagination is {}", pgCrit.toString());
    throwBindExceptionIfErrors(errors);

    PaginationCriteria<BaseRecord> internalPgCrit =
        getPaginationCriteriaForApiSearch(pgCrit, BaseRecord.class);
    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setMediaFilesFilter(true);
    filters.setMediaFilesType(srchConfig.getMediaType());

    // searching
    ISearchResults<BaseRecord> mediaFilesResults =
        recordManager.getFilteredRecords(filters, internalPgCrit, user);

    ApiFileSearchResult apiSearchResult = new ApiFileSearchResult();
    List<ApiFile> fileList = new ArrayList<>();
    convertISearchResults(
        pgCrit,
        srchConfig,
        user,
        mediaFilesResults,
        apiSearchResult,
        fileList,
        file -> new ApiFile((EcatMediaFile) file),
        info -> addFileLink(info));
    return apiSearchResult;
  }

  @Override
  public ApiFile getFileById(@PathVariable Long id, @RequestAttribute(name = "user") User user) {
    ApiFile file = fileHandler.getFile(id, user);
    if (file == null) {
      throw new NotFoundException(createNotFoundMessage("File", id));
    }
    addFileLink(file);
    return file;
  }

  @Override
  public void getFileBytes(
      @PathVariable Long id,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {

    EcatMediaFile mediaFile = fileHandler.getMediaFileIfPermitted(id, user, true);

    if (mediaFile == null) {
      throw new NotFoundException(createNotFoundMessage("File", id));
    }
    FileProperty fileProp = mediaFile.getFileProperty();

    response.setContentType(mediaFile.getContentType());
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + mediaFile.getFileName() + "\"");
    Optional<FileInputStream> fis = fileStore.retrieve(fileProp);
    if (fis.isPresent()) {
      try (InputStream is = fis.get();
          ServletOutputStream out = response.getOutputStream(); ) {
        IOUtils.copy(is, out);
      }
    } else {
      log.error("Could not retrieve file input stream on downloaded file");
    }
  }

  @Override
  public ApiFile uploadFiles(
      @RequestParam(value = "folderId", required = false) Long folderId,
      @RequestParam(value = "caption", required = false, defaultValue = "") String caption,
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(name = "user") User user)
      throws BindException, IOException {
    caption = StringUtils.trimToEmpty(caption);
    validateCaptionMaxLength(caption);
    InputStream inputStream = file.getInputStream();
    String originalFileName = file.getOriginalFilename();
    // RA for dev/test, not prod.
    ensureUserInitialised(user);
    String contentType = MediaUtils.extractFileTypeFromPath(originalFileName);
    Folder targetFolder = null;
    synchronized (API_INBOX_LOCK) {
      targetFolder = folderMgr.getApiUploadTargetFolder(contentType, user, folderId);
    }
    EcatMediaFile emf =
        mediaMgr.saveMediaFile(
            inputStream,
            null,
            originalFileName,
            originalFileName,
            null,
            targetFolder,
            caption,
            user);
    ApiFile apifile = new ApiFile(emf);
    addFileLink(apifile);
    return apifile;
  }

  private void ensureUserInitialised(User user) {
    if (!user.isContentInitialized()) {
      contentInit.initialiseUser(user);
    }
  }

  private void validateCaptionMaxLength(String caption) {
    Validate.isTrue(
        caption.length() <= EditInfo.DESCRIPTION_LENGTH,
        getMessage("errors.maxlength", new Object[] {"caption", EditInfo.DESCRIPTION_LENGTH}));
  }

  @Override
  public ApiFile updateFile(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(name = "user") User user)
      throws BindException, IOException, URISyntaxException {

    ApiFile updated =
        recordManager
            .getSafeNull(id)
            .filter(BaseRecord::isMediaRecord)
            .filter(r -> permUtils.isPermitted(r, PermissionType.WRITE, user))
            .map(r -> doUpload(id, file, user))
            .orElseThrow(() -> new NotFoundException(createNotFoundMessage("File", id)));
    return updated;
  }

  @SneakyThrows(IOException.class)
  private ApiFile doUpload(Long id, MultipartFile file, User user) {
    InputStream inputStream = file.getInputStream();
    String originalFileName = file.getOriginalFilename();
    EcatMediaFile emf =
        mediaMgr.saveMediaFile(
            inputStream, id, originalFileName, originalFileName, null, null, null, user);
    ApiFile apifile = new ApiFile(emf);
    addFileLink(apifile);
    return apifile;
  }
}
