package com.researchspace.webapp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.NfsViewProperty;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserKeyManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller serving the filestore login dialog and handling per-link filestore actions (current
 * path lookup and single-file download) triggered from filestore links inside documents/notebooks.
 */
@DeploymentProperty(DeploymentPropertyType.NET_FILE_STORES_ENABLED)
@Controller
@RequestMapping("/netFiles/ajax")
public class NfsController extends BaseController {

  protected static final String SUCCESS_MSG = "ok";
  protected static final String NEED_LOG_IN_MSG = "need.log.in";

  protected static final String SESSION_NFS_CLIENTS = "SESSION_NFS_CLIENTS";
  protected static final String SESSION_NFS_DOWNLOAD_PATH = "SESSION_NFS_DOWNLOAD_PATH";

  private static final String NO_FILE_PATHS_IN_DIR_NAME =
      "netfilestores.login.no.file.paths.in.dir";
  private static final int DOWNLOAD_BUFFER_SIZE = 1024;

  @Autowired private NfsManager nfsManager;

  @Autowired private NfsFileHandler nfsFileHandler;

  @Autowired private UserKeyManager userKeyManager;

  @Autowired @Setter private FilestoreAclChecker aclChecker;

  /**
   * @return a view of login dialog for file system
   */
  @GetMapping("/netFilesLoginView")
  public ModelAndView getNetFilesLoginView(Model model, Principal p) {
    addFileStoresAndFileSystemsToView(model, p);
    return new ModelAndView("netfiles/netFiles_login");
  }

  private void addFileStoresAndFileSystemsToView(Model model, Principal p) {
    User principalUser = getPrincipalUser(p);
    List<NfsFileStoreInfo> userStoreInfos = nfsManager.getFileStoreInfosForUser(principalUser);
    List<NfsFileSystemInfo> activeSystemInfos = nfsManager.getActiveFileSystemInfos(principalUser);

    String userStoresJson = null;
    String activeSystemsJson = null;
    try {
      ObjectMapper mapper = new ObjectMapper();
      userStoresJson = mapper.writeValueAsString(userStoreInfos);
      activeSystemsJson = mapper.writeValueAsString(activeSystemInfos);
    } catch (JsonProcessingException e) {
      log.warn("couldn't serialize filestore or filesystem", e);
    }
    String escapedUserStoresJson = StringEscapeUtils.escapeEcmaScript(userStoresJson); // RSPAC-2090
    model.addAttribute(NfsViewProperty.FILE_STORES_JSON.toString(), escapedUserStoresJson);
    String escapedFileSystemsJson =
        StringEscapeUtils.escapeEcmaScript(activeSystemsJson); // RSPAC-2360
    model.addAttribute(NfsViewProperty.FILE_SYSTEMS_JSON.toString(), escapedFileSystemsJson);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NfsLoginData {
    private Long fileSystemId;
    private String nfsusername;
    private String nfspassword;
    private String nfsuserdir;

    public NfsLoginData(Long fileSystemId, String nfsusername, String nfspassword) {
      this(fileSystemId, nfsusername, nfspassword, null);
    }
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"nfspassword"})
  @PostMapping("/nfsLogin")
  @ResponseBody
  public String loginToNfs(
      @RequestParam(value = "fileSystemId") Long fileSystemId,
      @RequestParam(value = "nfsusername", required = false) String nfsusername,
      @RequestParam(value = "nfspassword", required = false) String nfspassword,
      @RequestParam(value = "nfsuserdir", required = false) String nfsuserdir,
      HttpServletRequest request,
      Principal p) {

    return loginToNfsWithJson(
        new NfsLoginData(fileSystemId, nfsusername, nfspassword, nfsuserdir), request, p);
  }

  @PostMapping("/nfsLoginJson")
  @ResponseBody
  public String loginToNfsWithJson(
      @RequestBody NfsLoginData nfsLoginData, HttpServletRequest request, Principal p) {
    String targetDirectory = nfsLoginData.getNfsuserdir();
    if (targetDirectory != null) {
      if (targetDirectory.indexOf("/") != -1 || targetDirectory.indexOf("\\") != -1)
        return getText(NO_FILE_PATHS_IN_DIR_NAME);
    }
    User user = getPrincipalUser(p);
    aclChecker.assertCanRead(user, nfsManager.getFileSystem(nfsLoginData.getFileSystemId()));
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    String loginResult =
        nfsManager.loginToNfs(
            nfsLoginData.getFileSystemId(),
            nfsLoginData.getNfsusername(),
            nfsLoginData.getNfspassword(),
            nfsClients,
            user,
            targetDirectory);

    if (loginResult == null) {
      return NEED_LOG_IN_MSG; // not logged in
    }
    if (loginResult.startsWith(NfsManager.LOGGED_AS_MSG)) {
      return loginResult; // logged in fine
    }
    return getText(loginResult); // error code, let's translate
  }

  /*
   * ==========================
   * public key authentication managing methods
   *
   * No longer triggered from the RSpace UI after the old Gallery removal, but the URLs
   * are kept so external scripts / integrations relying on them continue to work.
   * ==========================
   */

  /** Kept for URL stability — no longer called from the RSpace UI. */
  @PostMapping("/nfsRegisterKey")
  @ResponseBody
  public String registerNewKeyForNfs(Principal principal) {
    UserKeyPair registerNewKey = userKeyManager.createNewUserKeyPair(getPrincipalUser(principal));
    return registerNewKey.getPublicKey();
  }

  /** Kept for URL stability — no longer called from the RSpace UI. */
  @GetMapping("/nfsRemoveKey")
  @ResponseBody
  public String removeKeyForNfs(Principal principal) {
    userKeyManager.removeUserKeyPair(getPrincipalUser(principal));
    return SUCCESS_MSG;
  }

  /** Kept for URL stability — no longer called from the RSpace UI. */
  @GetMapping("/nfsPublicKeyRegistrationUrl")
  @ResponseBody
  public String getNfsPublicKeyRegistrationUrl(
      @RequestParam(value = "fileSystemId") Long fileSystemId) {
    return nfsManager
        .getFileSystem(fileSystemId)
        .getAuthOption(NfsFileSystemOption.PUBLIC_KEY_REGISTRATION_DIALOG_URL);
  }

  @PostMapping("/getCurrentPath")
  @ResponseBody
  public String getCurrentPath(
      @RequestParam("namepath") String namePath,
      @RequestParam(name = "nfsId", required = false) Long nfsId,
      HttpServletRequest request,
      Principal p) {

    if (StringUtils.isEmpty(namePath) || !namePath.contains(":")) {
      throw new IllegalArgumentException("wrong format of namePath: " + namePath);
    }

    User user = getPrincipalUser(p);
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    /* links to file store documents are stored as fileStoreId:relativePath,
     * so this method resolves such link to full path */
    String[] namePathArray = namePath.split(":");
    Long fileStoreId = Long.valueOf(namePathArray[0]);
    String path = namePathArray[1];
    NfsTarget nfsTarget = new NfsTarget(path, nfsId);

    NfsFileStore fileStore = nfsManager.getNfsFileStore(fileStoreId);
    if (fileStore == null) {
      throw new IllegalArgumentException("could not find file store with id: " + fileStoreId);
    }
    aclChecker.assertCanRead(user, fileStore.getFileSystem());

    Long fileSystemId = fileStore.getFileSystem().getId();
    if (!nfsManager.checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      return NEED_LOG_IN_MSG;
    }

    NfsClient nfsClient = getNfsClientFromMap(fileSystemId, nfsClients);
    NfsFileDetails nfsFileDetails = nfsFileHandler.getCurrentPath(nfsTarget, nfsClient);
    return nfsFileDetails.getFileSystemFullPath();
  }

  /*
   * =============================
   * file download methods
   * =============================
   */

  /**
   * called as a first (of two) step when downloading a file from net file store. downloads selected
   * file from external store and saves it on RSpace server.
   */
  @PostMapping("/prepareNfsFileForDownload")
  @ResponseBody
  public String prepareNfsFileForDownload(
      @RequestParam(name = "namepath") String namePath,
      @RequestParam(name = "nfsId", required = false) Long nfsId,
      HttpServletRequest request,
      Principal p) {

    if (StringUtils.isEmpty(namePath) || !namePath.contains(":")) {
      throw new IllegalArgumentException("wrong format of namePath: " + namePath);
    }

    User user = getPrincipalUser(p);
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    /* links to file store documents are stored as fileStoreId:relativePath,
     * so this method resolves such link to full path */
    String[] namePathArray = namePath.split(":");
    Long fileStoreId = Long.valueOf(namePathArray[0]);
    String relativePath = namePathArray[1];

    NfsFileStore fileStore = nfsManager.getNfsFileStore(fileStoreId);
    if (fileStore == null) {
      throw new IllegalArgumentException("could not find file store with id: " + fileStoreId);
    }
    aclChecker.assertCanRead(user, fileStore.getFileSystem());

    Long fileSystemId = fileStore.getFileSystem().getId();
    if (!nfsManager.checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      return NEED_LOG_IN_MSG;
    }

    NfsClient nfsClient = getNfsClientFromMap(fileSystemId, nfsClients);
    String fullPath = fileStore.getAbsolutePath(relativePath);
    try {
      NfsFileDetails nfsFileDetails =
          nfsFileHandler.downloadNfsFileToRSpace(new NfsTarget(fullPath, nfsId), nfsClient);
      String localPath = nfsFileDetails.getLocalFile().getCanonicalPath();
      request.getSession().setAttribute(SESSION_NFS_DOWNLOAD_PATH, localPath);

    } catch (IOException ex) {
      log.warn(ex.getMessage(), ex);
      return getText("net.filestores.error.download");
    }

    return SUCCESS_MSG;
  }

  /**
   * called as a second (of two) step when downloading a file from net file store. the file is
   * already on RSpace server, so it's streamed to response.
   */
  @GetMapping("/downloadNfsFile")
  @ResponseBody
  public void downloadNfsFile(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    String path = (String) request.getSession().getAttribute(SESSION_NFS_DOWNLOAD_PATH);
    if (StringUtils.isEmpty(path)) {
      return;
    }

    File file = new File(path);
    File parentDir = file.getParentFile();

    try (FileInputStream in = new FileInputStream(file)) {
      response.setContentType("application/octet-stream");
      response.setContentLength((int) file.length());
      response.setHeader(
          "Content-Disposition", String.format("attachment; filename=\"%s\"", file.getName()));

      OutputStream outStream = response.getOutputStream();
      byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
      int bytesRead = -1;
      while ((bytesRead = in.read(buffer)) != -1) {
        outStream.write(buffer, 0, bytesRead);
      }
    } finally {
      if (file.exists()) {
        FileUtils.forceDelete(file);
      }
      if (parentDir.exists()) {
        FileUtils.forceDelete(parentDir);
      }
    }
  }

  @GetMapping("/nfsFileStoreInfo")
  @ResponseBody
  public AjaxReturnObject<NfsFileStoreInfo> getNfsFileStoreInfo(
      @RequestParam("fileStoreId") Long fileStoreId) {
    NfsFileStore nfsFileStore = nfsManager.getNfsFileStore(fileStoreId);
    NfsFileStoreInfo fileStoreInfo = new NfsFileStoreInfo(nfsFileStore);
    return new AjaxReturnObject<>(fileStoreInfo, null);
  }

  /** Retrieves map of NfsClients users is logged into. */
  @SuppressWarnings("unchecked")
  public Map<Long, NfsClient> retrieveNfsClientsMapFromSession(HttpServletRequest request) {
    Map<Long, NfsClient> nfsClients =
        (Map<Long, NfsClient>) request.getSession().getAttribute(SESSION_NFS_CLIENTS);
    if (nfsClients == null) {
      nfsClients = new HashMap<>();
      request.getSession().setAttribute(SESSION_NFS_CLIENTS, nfsClients);
    }
    return nfsClients;
  }

  /*
   * ==========================
   * helper methods
   * ==========================
   */

  private NfsClient getNfsClientFromMap(Long fileSystemId, Map<Long, NfsClient> nfsClients) {
    return nfsClients.get(fileSystemId);
  }

  private User getPrincipalUser(Principal p) {
    return userManager.getUserByUsername(p.getName());
  }

  /*
   * ==========================
   * for tests
   * ==========================
   */

  protected void setNfsManager(NfsManager nfsManager) {
    this.nfsManager = nfsManager;
  }
}
