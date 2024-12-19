package com.researchspace.webapp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.core.util.LoggingUtils;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileStoreInfo;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemInfo;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFileTreeNode;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.netfiles.NfsViewProperty;
import com.researchspace.service.NfsFileHandler;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserKeyManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
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
 * Controller handling client-side actions on Filestores Gallery page, also handles single file
 * streaming after clicking on a download link.
 */
@DeploymentProperty(DeploymentPropertyType.NET_FILE_STORES_ENABLED)
@Controller
@RequestMapping("/netFiles/ajax")
public class NfsController extends BaseController {

  protected static final String SUCCESS_MSG = "ok";
  protected static final String NEED_LOG_IN_MSG = "need.log.in";

  protected static final String MODEL_TREE_NODE = "nfsTreeNode";
  protected static final String MODEL_DIR = "dir";
  protected static final String MODEL_ADDING_FILE_STORE_FLAG = "addingFileStore";
  protected static final String MODEL_SHOW_EXTRA_DIRS_FLAG = "showExtraDirs";
  protected static final String MODEL_SHOW_CURRENT_DIR_FLAG = "showCurrentDir";
  protected static final String MODEL_FILESYSTEM_TYPE = "fileSystemType";
  public static final String PROBLEM_LOADING_FILES = "PROBLEM_LOADING_FILES";

  protected static final String SESSION_NFS_CLIENTS = "SESSION_NFS_CLIENTS";
  protected static final String SESSION_NFS_DOWNLOAD_PATH = "SESSION_NFS_DOWNLOAD_PATH";

  private static final String NO_FILE_PATHS_IN_DIR_NAME =
      "netfilestores.login.no.file.paths.in.dir";
  private static final int DOWNLOAD_BUFFER_SIZE = 1024;
  private static final String ERRORID = "errorId";

  @Autowired private NfsManager nfsManager;

  @Autowired private NfsFileHandler nfsFileHandler;

  @Autowired private UserKeyManager userKeyManager;

  /*
   * =========================
   * net files gallery navigation methods
   * =========================
   */

  /**
   * @return a view of Gallery->Filestores page
   */
  @GetMapping("/netFilesGalleryView")
  public ModelAndView getNetFilesGalleryView(Model model, Principal p) {
    addFileStoresAndFileSystemsToView(model, p);

    UserKeyPair userKeyPair = userKeyManager.getUserKeyPair(getPrincipalUser(p));
    if (userKeyPair != null) {
      model.addAttribute(
          NfsViewProperty.PUBLIC_KEY.toString(),
          StringEscapeUtils.escapeJavaScript(userKeyPair.getPublicKey()));
    }
    return new ModelAndView("netfiles/netFiles");
  }

  /**
   * @return a view of login dialog for file system
   */
  @GetMapping("/netFilesLoginView")
  public ModelAndView getNetFilesLoginView(Model model, Principal p) {
    addFileStoresAndFileSystemsToView(model, p);
    return new ModelAndView("netfiles/netFiles_login");
  }

  private void addFileStoresAndFileSystemsToView(Model model, Principal p) {
    List<NfsFileStoreInfo> userStoreInfos =
        nfsManager.getFileStoreInfosForUser(getPrincipalUser(p));
    List<NfsFileSystemInfo> activeSystemInfos = nfsManager.getActiveFileSystemInfos();

    String userStoresJson = null;
    String activeSystemsJson = null;
    try {
      ObjectMapper mapper = new ObjectMapper();
      userStoresJson = mapper.writeValueAsString(userStoreInfos);
      activeSystemsJson = mapper.writeValueAsString(activeSystemInfos);
    } catch (JsonProcessingException e) {
      log.warn("couldn't serialize filestore or filesystem", e);
    }
    String escapedUserStoresJson = StringEscapeUtils.escapeJavaScript(userStoresJson); // RSPAC-2090
    model.addAttribute(NfsViewProperty.FILE_STORES_JSON.toString(), escapedUserStoresJson);
    String escapedFileSystemsJson =
        StringEscapeUtils.escapeJavaScript(activeSystemsJson); // RSPAC-2360
    model.addAttribute(NfsViewProperty.FILE_SYSTEMS_JSON.toString(), escapedFileSystemsJson);
  }

  /**
   * @return "logged.as.username" when successfully connected, "need.log.in" if not logged in, or
   *     error msg for other problems
   */
  @PostMapping("/tryConnectToFileSystemRoot")
  @ResponseBody
  public String tryConnectToFileSystemRoot(
      @RequestParam(value = "fileSystemId") Long fileSystemId,
      @RequestParam(value = "targetDir", required = false) String targetDir,
      HttpServletRequest request,
      Principal p) {

    User user = getPrincipalUser(p);
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    if (!nfsManager.checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      return NEED_LOG_IN_MSG;
    }
    return testConnectionToTarget(targetDir != null ? targetDir : "", fileSystemId, nfsClients);
  }

  /**
   * @return "logged.as.-username-" when successfully connected, "need.log.in" if not logged in, or
   *     error msg for other problems
   */
  @GetMapping("/tryConnectToFileStore")
  @ResponseBody
  public String tryConnectToFileStore(
      @RequestParam("fileStoreId") Long fileStoreId, HttpServletRequest request, Principal p) {

    User user = getPrincipalUser(p);
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    NfsFileStore fileStore = getUserFileStoreFromId(fileStoreId, user);
    Long fileSystemId = fileStore.getFileSystem().getId();

    if (!nfsManager.checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      return NEED_LOG_IN_MSG;
    }

    String path = NfsFileStore.validateTargetPath(fileStore.getPath());
    return testConnectionToTarget(path, fileSystemId, nfsClients);
  }

  private String testConnectionToTarget(
      String target, Long fileSystemId, Map<Long, NfsClient> nfsClients) {
    NfsClient nfsClient = getNfsClientFromMap(fileSystemId, nfsClients);
    String result = nfsManager.testConnectionToTarget(target, fileSystemId, nfsClient);
    if (!result.startsWith(NfsManager.LOGGED_AS_MSG)) {
      // translate error code
      result = getText(result);
    }
    return result;
  }

  /**
   * retrieves a jsp with tree structure for jquery.fileTree used on Gallery->Filestores page
   *
   * @param fileSystemId if provided returns tree starting at file system root, for adding a
   *     filestore
   * @param fileStoreId if provided returns tree starting at file store location, for browsing a
   *     filestore
   * @param dirPath returns branch starting at particular path (called after expanding a tree node)
   * @param nfsOrder decides about tree nodes order, accepts "byname" or "bydate" values
   */
  @PostMapping("/nfsFileTree")
  public String retrieveNfsFileTree(
      @RequestParam(value = "fileSystemId", required = false) Long fileSystemId,
      @RequestParam(value = "fileStoreId", required = false) Long fileStoreId,
      @RequestParam("dir") String dirPath,
      @RequestParam(value = "order", defaultValue = "byname") String nfsOrder,
      HttpServletRequest request,
      Model model,
      Principal p) {

    if (fileSystemId == null && fileStoreId == null) {
      throw new IllegalArgumentException(
          "either file system id or file store id have to be provided");
    }

    User user = getPrincipalUser(p);
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);

    boolean browsingUserFileStore = fileStoreId != null;
    NfsFileStore userFileStore = null;
    if (browsingUserFileStore) {
      userFileStore = getUserFileStoreFromId(fileStoreId, user);
      fileSystemId = userFileStore.getFileSystem().getId();
    }
    assertUserLoggedIn(fileSystemId, request, user);

    if (browsingUserFileStore && "".equals(dirPath)) {
      dirPath = userFileStore.getPath();
    }
    dirPath = URLDecoder.decode(dirPath, StandardCharsets.UTF_8);
    dirPath = dirPath.replaceAll("//", "/");

    try {
      NfsClient nfsClient = getNfsClientFromMap(fileSystemId, nfsClients);
      NfsFileTreeNode nodeTree = nfsClient.createFileTree(dirPath, nfsOrder, userFileStore);
      NfsFileSystem targetFileSystem = nfsManager.getFileSystem(fileSystemId);
      model.addAttribute(MODEL_DIR, dirPath);
      model.addAttribute(MODEL_TREE_NODE, nodeTree);
      model.addAttribute(MODEL_ADDING_FILE_STORE_FLAG, !browsingUserFileStore);
      model.addAttribute(
          MODEL_SHOW_EXTRA_DIRS_FLAG,
          nfsClient.supportsExtraDirs() && !targetFileSystem.fileSystemRequiresUserRootDirs());
      model.addAttribute(MODEL_SHOW_CURRENT_DIR_FLAG, nfsClient.supportsCurrentDir());
      model.addAttribute(MODEL_FILESYSTEM_TYPE, targetFileSystem.getClientType());
    } catch (IOException ex) {
      String errorID = LoggingUtils.generateLogId();
      String rmsg =
          "errorId-"
              + errorID
              + " Retrieve Failed: cannot retrieve file from "
              + dirPath
              + ": "
              + ex.getMessage();
      log.error(rmsg, ex);
      model.addAttribute(PROBLEM_LOADING_FILES, true);
      model.addAttribute(ERRORID, errorID);
    }

    return "netfiles/netFiles_fileTree";
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

  @PostMapping("/nfsLogout")
  @ResponseBody
  public String logoutFromNfs(
      @RequestParam(value = "fileSystemId") Long fileSystemId, HttpServletRequest request) {
    nfsManager.logoutFromNfs(fileSystemId, retrieveNfsClientsMapFromSession(request));
    return SUCCESS_MSG;
  }

  /*
   * ==========================
   * public key authentication managing methods
   * ==========================
   *
   */
  @PostMapping("/nfsRegisterKey")
  @ResponseBody
  public String registerNewKeyForNfs(Principal principal) {
    UserKeyPair registerNewKey = userKeyManager.createNewUserKeyPair(getPrincipalUser(principal));
    return registerNewKey.getPublicKey();
  }

  /**
   * This method is not called from front-end, but URL can be called by anyone who want to test
   * registration workflow.
   *
   * @param principal
   * @return
   */
  @GetMapping("/nfsRemoveKey")
  @ResponseBody
  public String removeKeyForNfs(Principal principal) {
    userKeyManager.removeUserKeyPair(getPrincipalUser(principal));
    return SUCCESS_MSG;
  }

  @GetMapping("/nfsPublicKeyRegistrationUrl")
  @ResponseBody
  public String getNfsPublicKeyRegistrationUrl(
      @RequestParam(value = "fileSystemId") Long fileSystemId) {
    return nfsManager
        .getFileSystem(fileSystemId)
        .getAuthOption(NfsFileSystemOption.PUBLIC_KEY_REGISTRATION_DIALOG_URL);
  }

  /*
   * ==========================
   * file store managing methods
   * ==========================
   */
  @PostMapping("/saveFileStore")
  @ResponseBody
  public AjaxReturnObject<String> saveFileStore(
      @RequestParam("fileSystemId") Long fileSystemId,
      @RequestParam("nfsname") String fileStoreName,
      @RequestParam("nfspath") String fileStorePath,
      HttpServletRequest request,
      Principal p) {

    User user = getPrincipalUser(p);
    assertUserLoggedIn(fileSystemId, request, user);

    if (StringUtils.isEmpty(fileStoreName)) {
      return new AjaxReturnObject<>(
          null,
          getErrorListFromMessageCode(getText("net.filestores.validation.userfolder.name.empty")));
    }

    boolean filestoreNameUnique = nfsManager.verifyFileStoreNameUniqueForUser(fileStoreName, user);
    if (!filestoreNameUnique) {
      return new AjaxReturnObject<>(
          null,
          getErrorListFromMessageCode(
              getText(
                  "net.filestores.validation.userfolder.name.not.unique",
                  new String[] {fileStoreName})));
    }

    NfsFileStore userStore =
        nfsManager.createAndSaveNewFileStore(fileSystemId, fileStoreName, fileStorePath, user);

    String userStoreJson = null;
    try {
      userStoreJson = (new ObjectMapper()).writeValueAsString(userStore.toFileStoreInfo());
    } catch (JsonProcessingException e) {
      log.warn("couldn't serialize created filestore", e);
    }

    return new AjaxReturnObject<>(userStoreJson, null);
  }

  @PostMapping("/deleteFileStore")
  @ResponseBody
  public String deleteFileStore(@RequestParam("fileStoreId") Long fileStoreId, Principal p) {

    User user = getPrincipalUser(p);
    NfsFileStore userFolder = getUserFileStoreFromId(fileStoreId, user);
    nfsManager.markFileStoreAsDeleted(userFolder);
    return SUCCESS_MSG;
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

  /** throws exception if fileStoreId doesn't exist or doesn't belong to current user */
  private NfsFileStore getUserFileStoreFromId(Long fileStoreId, User user) {

    NfsFileStore fileStore = nfsManager.getNfsFileStore(fileStoreId);
    if (fileStore == null) {
      throw new IllegalArgumentException("could not find user folder with id: " + fileStoreId);
    }
    if (!user.getUsername().equals(fileStore.getUser().getUsername())) {
      throw new IllegalArgumentException(
          user.getUsername() + " asked for someone else's filestore: " + fileStore);
    }
    return fileStore;
  }

  private void assertUserLoggedIn(Long fileSystemId, HttpServletRequest request, User user) {
    Map<Long, NfsClient> nfsClients = retrieveNfsClientsMapFromSession(request);
    if (!nfsManager.checkIfUserLoggedIn(fileSystemId, nfsClients, user)) {
      throw new IllegalStateException("user not logged into filesystem " + fileSystemId);
    }
  }

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
