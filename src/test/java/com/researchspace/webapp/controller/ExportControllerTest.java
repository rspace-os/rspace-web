package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.system.SystemPropertyTestFactory.createAnyAppWithConfigElements;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.NfsExportConfig;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO.ArchiveDialogConfig;
import com.researchspace.model.dtos.export.ExportDialogConfigDTO;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepositoryTestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.GroupManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.impl.OntologyDocManager;
import com.researchspace.webapp.controller.ExportController.ServerPath;
import java.io.File;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import javax.servlet.http.HttpSession;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

public class ExportControllerTest {

  private static final String WORD = "WORD";

  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Mock private UserManager mockUserMgr;
  @Mock private ImportStrategy importStrategyMock;
  @Mock private Principal principal;
  @Mock private ExportImport exImportMgr;
  @Mock private IExportUtils pdfUtils;

  @Mock private IPropertyHolder properties;
  @Mock private UserAppConfigManager appCfgMgr;
  @Mock private BindingResult errors;
  @Mock private BindingResult bindingResult;
  @Mock private RepositoryDepositHandler depositHandler;
  @Mock private GroupManager groupManager;
  @Mock private DiskSpaceChecker diskSpaceChecker;
  @Mock private OntologyDocManager ontologyDocManager;
  @Mock IGroupPermissionUtils grpPermUtils;

  @InjectMocks private ExportController exportController;

  private StaticMessageSource mockMessageSource;
  private HttpSession session;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  private User user;
  private MultipartFile oKfile;
  private RedirectAttributes redirectAttributesMap;
  private byte[] any_bytes;

  @Before
  public void setUp() throws Exception {
    setupMessageSources();

    session = new MockHttpSession();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    exportController.setMessageSource(new MessageSourceUtils(mockMessageSource));
    exportController.setResponseUtil(new ResponseUtil());

    user = TestFactory.createAnyUser("user1a");

    when(diskSpaceChecker.canStartArchiveProcess()).thenReturn(true);
  }

  private void setupMessageSources() {
    mockMessageSource = new StaticMessageSource();
    mockMessageSource.addMessage("errors.too.manyitems", Locale.getDefault(), "toomany");
    mockMessageSource.addMessage("errors.too.fewitems", Locale.getDefault(), "toofew");
    mockMessageSource.addMessage("errors.required", Locale.getDefault(), "required");
    mockMessageSource.addMessage("errors.maxlength", Locale.getDefault(), "toolong");
    mockMessageSource.addMessage("repository.successMsg", Locale.getDefault(), "success");
    mockMessageSource.addMessage("repository.submitFailureMsg", Locale.getDefault(), "failure");
    mockMessageSource.addMessage("errors.invalidstringformat", Locale.getDefault(), "badformat");
    mockMessageSource.addMessage(
        "archive.download.failure.msg", Locale.getDefault(), "downloadfailed");
    mockMessageSource.addMessage("errors.unsupported", Locale.getDefault(), "unsupported");
    mockMessageSource.addMessage("record.inaccessible", Locale.getDefault(), "noapp");
    mockMessageSource.addMessage("invalid.app.choice", Locale.getDefault(), "badchoice");
    mockMessageSource.addMessage(
        "pdfArchiving.submission.successMsg", Locale.getDefault(), "success");
    mockMessageSource.addMessage("workspace.export.msgSuccess", Locale.getDefault(), "success");
    mockMessageSource.addMessage(
        "workspace.export.msgFailure", Locale.getDefault(), "failure: {1}");
    mockMessageSource.addMessage(
        "workspace.export.noDiskSpace", Locale.getDefault(), "failure: noDiskSpace");
  }

  private static ExportSelection createExportSelection(Long[] ids, String[] names, String[] types) {
    ExportSelection exportSelection = new ExportSelection();
    exportSelection.setType(ExportSelection.ExportType.SELECTION);
    exportSelection.setExportIds(ids);
    exportSelection.setExportNames(names);
    exportSelection.setExportTypes(types);
    return exportSelection;
  }

  private static RepoDepositConfig createRepoDepositConfig(boolean depositToRepository) {
    RepoDepositConfig repositoryConfig = new RepoDepositConfig();
    repositoryConfig.setMeta(RepositoryTestFactory.createAValidRepoDepositMeta());
    repositoryConfig.setAppName(App.APP_DATAVERSE);
    repositoryConfig.setDepositToRepository(depositToRepository);
    repositoryConfig.setRepoCfg(1L);
    return repositoryConfig;
  }

  private static ArchiveDialogConfig createArchiveDialogConfig() {
    ArchiveDialogConfig archiveDialogConfig = new ArchiveDialogConfig();
    archiveDialogConfig.setArchiveType("html");
    archiveDialogConfig.setDescription("sample description");
    archiveDialogConfig.setMaxLinkLevel(3);
    archiveDialogConfig.setAllVersions(false);
    return archiveDialogConfig;
  }

  private static NfsExportConfig createNfsDialogConfig() {
    return new NfsExportConfig();
  }

  public static ExportArchiveDialogConfigDTO createExportArchiveConfig(
      Long[] ids, String[] names, String[] types, boolean depositToRepository) {
    ExportArchiveDialogConfigDTO exportArchiveConfig = new ExportArchiveDialogConfigDTO();
    exportArchiveConfig.setExportSelection(createExportSelection(ids, names, types));
    exportArchiveConfig.setExportConfig(createArchiveDialogConfig());
    exportArchiveConfig.setNfsConfig(createNfsDialogConfig());
    exportArchiveConfig.setRepositoryConfig(createRepoDepositConfig(depositToRepository));
    return exportArchiveConfig;
  }

  public static ExportDialogConfigDTO createExportConfig(
      Long[] ids, String[] names, String[] types, ExportToFileConfig exportToFileConfig) {
    ExportDialogConfigDTO exportConfig = new ExportDialogConfigDTO();
    exportConfig.setExportSelection(createExportSelection(ids, names, types));
    exportConfig.setExportConfig(exportToFileConfig);
    exportConfig.setRepositoryConfig(createRepoDepositConfig(false));
    return exportConfig;
  }

  public static ExportDialogConfigDTO createExportConfigForUser(String username) {
    ExportSelection exportSelection = new ExportSelection();
    exportSelection.setType(ExportSelection.ExportType.USER);
    exportSelection.setUsername(username);

    ExportDialogConfigDTO exportConfig = new ExportDialogConfigDTO();
    exportConfig.setExportSelection(exportSelection);
    exportConfig.setExportConfig(new ExportToFileConfig());
    exportConfig.setRepositoryConfig(createRepoDepositConfig(false));

    return exportConfig;
  }

  public static ExportArchiveDialogConfigDTO createExportArchiveConfigForUser(String username) {
    ExportSelection exportSelection = new ExportSelection();
    exportSelection.setType(ExportSelection.ExportType.USER);
    exportSelection.setUsername(username);

    ExportArchiveDialogConfigDTO exportConfig = new ExportArchiveDialogConfigDTO();
    exportConfig.setExportSelection(exportSelection);
    exportConfig.setExportConfig(createArchiveDialogConfig());
    exportConfig.setNfsConfig(createNfsDialogConfig());
    exportConfig.setRepositoryConfig(createRepoDepositConfig(false));

    return exportConfig;
  }

  private static ExportDialogConfigDTO createExportConfigForGroup() {
    ExportSelection exportSelection = new ExportSelection();
    exportSelection.setType(ExportSelection.ExportType.GROUP);
    exportSelection.setGroupId(1L);

    ExportDialogConfigDTO exportConfig = new ExportDialogConfigDTO();
    exportConfig.setExportSelection(exportSelection);
    exportConfig.setExportConfig(new ExportToFileConfig());
    exportConfig.setRepositoryConfig(createRepoDepositConfig(false));

    return exportConfig;
  }

  private void setUpImportSuccessFile() throws Exception {
    any_bytes = new byte[] {0, 1, 2};
    oKfile = createOKMultipartFile(any_bytes);
    redirectAttributesMap = new RedirectAttributesModelMap();
    when(principal.getName()).thenReturn("user1a");
    when(exImportMgr.importArchive(
            Mockito.eq(oKfile),
            Mockito.eq("user1a"),
            Mockito.any(ArchivalImportConfig.class),
            Mockito.any(ProgressMonitor.class),
            Mockito.any(ImportStrategy.class)))
        .thenReturn(new ImportArchiveReport());
  }

  @Test
  public void testSuccessImportDoesFormUpdateUponontologyDocuments() throws Exception {
    setUpImportSuccessFile();
    // first test a successful import has no error messages and redirects to correct page
    assertEquals(
        "Success redirect not sent",
        "redirect:/import/archiveImportReport",
        exportController.importArchive(oKfile, session, redirectAttributesMap, principal));
    verify(ontologyDocManager).updateImportedOntologiesWithCorrectForm("user1a");
  }

  @Test
  public void testErrorHandlingOfImport() throws Exception {
    setUpImportSuccessFile();
    // first test a successful import has no error messages and redirects to correct page
    assertEquals(
        "Success redirect not sent",
        "redirect:/import/archiveImportReport",
        exportController.importArchive(oKfile, session, redirectAttributesMap, principal));
    assertFalse(
        redirectAttributesMap
            .getFlashAttributes()
            .keySet()
            .contains(ExportController.IMPORT_FORM_ERROR_ATTR_NAME));
    mockMessageSource.addMessage("importArchive.badformat.msg", Locale.getDefault(), "badformat");

    // now test error scenarios: empty file rejected
    MultipartFile EMPTYfile =
        new MockMultipartFile("archive.zip", "archive.zip", "zip", new byte[] {});
    exportController.importArchive(EMPTYfile, session, redirectAttributesMap, principal);
    assertTrue(
        redirectAttributesMap
            .getFlashAttributes()
            .keySet()
            .contains(ExportController.IMPORT_FORM_ERROR_ATTR_NAME));
    redirectAttributesMap.getFlashAttributes().clear();
    // non-zip file rejected
    MultipartFile NON_ZIPfile = new MockMultipartFile("image.png", "image.png", "png", any_bytes);
    exportController.importArchive(NON_ZIPfile, session, redirectAttributesMap, principal);
    assertTrue(
        redirectAttributesMap
            .getFlashAttributes()
            .keySet()
            .contains(ExportController.IMPORT_FORM_ERROR_ATTR_NAME));
    redirectAttributesMap.getFlashAttributes().clear();
  }

  private MultipartFile createOKMultipartFile(byte[] ANY_BYTES) {
    return new MockMultipartFile("archive.zip", "archive.zip", "zip", ANY_BYTES);
  }

  @Test
  public void handleArchiveException() throws Exception {
    byte[] ANY_BYTES = new byte[] {0, 1, 2};
    MultipartFile OKfile = createOKMultipartFile(ANY_BYTES);
    RedirectAttributes ra = new RedirectAttributesModelMap();
    // handle exception in import code
    mockMessageSource.addMessage("importArchive.failure.msg", Locale.getDefault(), "any");
    when(principal.getName()).thenReturn("user1a");
    when(exImportMgr.importArchive(
            Mockito.eq(OKfile),
            Mockito.eq("user1a"),
            Mockito.any(ArchivalImportConfig.class),
            Mockito.any(ProgressMonitor.class),
            Mockito.any(ImportStrategy.class)))
        .thenThrow(new RuntimeException());
    exportController.importArchive(OKfile, session, ra, principal);
    assertTrue(
        ra.getFlashAttributes().keySet().contains(ExportController.IMPORT_FORM_ERROR_ATTR_NAME));
    ra.getFlashAttributes().clear();
  }

  @Test
  public void exportArchiveSelectionValidation() throws Exception {
    Long[] tooMany = ids(ExportController.maxIdsToProcess + 1);
    String[] tooManyTypes = types(ExportController.maxIdsToProcess + 1);
    String[] tooManyNames = names(ExportController.maxIdsToProcess + 1);
    ExportArchiveDialogConfigDTO tooManyConfig =
        createExportArchiveConfig(tooMany, tooManyNames, tooManyTypes, false);

    when(diskSpaceChecker.canStartArchiveProcess()).thenReturn(false);

    String msgTooMany = exportController.exportArchive(tooManyConfig, errors, request, principal);
    assertEquals("failure: toomany", msgTooMany);

    ExportArchiveDialogConfigDTO okConfig =
        createExportArchiveConfig(ids(1), types(1), names(1), false);
    String msgDiskSpace = exportController.exportArchive(okConfig, errors, request, principal);
    assertEquals("failure: noDiskSpace", msgDiskSpace);
  }

  @Test
  public void exportSelectionValidation() {
    Long[] tooMany = ids(ExportController.maxIdsToProcess + 1);
    String[] tooManyTypes = types(ExportController.maxIdsToProcess + 1);
    String[] tooManyNames = names(ExportController.maxIdsToProcess + 1);
    String msg =
        exportController.export(
            createExportConfig(tooMany, tooManyNames, tooManyTypes, new ExportToFileConfig()),
            errors,
            principal);
    assertEquals("failure: toomany", msg);
  }

  @Test
  public void exportSelectionThrowsUOEIfMultiWordExport() {
    Long[] multi = ids(2);
    String[] multiTypes = types(2);
    String[] multiNames = names(2);
    ExportToFileConfig cfg = new ExportToFileConfig();
    cfg.setExportFormat(WORD);
    String msg =
        exportController.export(
            createExportConfig(multi, multiNames, multiTypes, cfg), errors, principal);
    assertEquals("failure: unsupported", msg);
  }

  private String[] names(int size) {
    String[] tooManyNames = new String[size];
    Arrays.fill(tooManyNames, "name");
    return tooManyNames;
  }

  private String[] types(int size) {
    String[] tooManyTypes = new String[size];
    Arrays.fill(tooManyTypes, RecordType.NORMAL.name());
    return tooManyTypes;
  }

  private Long[] ids(int size) {
    Long[] tooMany = new Long[size];
    Arrays.fill(tooMany, 1L);
    return tooMany;
  }

  @Test
  public void testExportPdfSelection() throws Exception {
    final Long[] exportIds = new Long[1];
    exportIds[0] = 1L;
    final String[] exportNames = {"Untitled Document"};
    final String[] exportTypes = {"NORMAL"};
    final ExportToFileConfig defaultCfg = new ExportToFileConfig();
    defaultCfg.setExportName("xxx");

    Mockito.when(principal.getName()).thenReturn("user1a");
    Mockito.when(mockUserMgr.getUserByUsername("user1a")).thenReturn(user);

    String rst =
        exportController.export(
            createExportConfig(exportIds, exportNames, exportTypes, defaultCfg), errors, principal);
    Mockito.verify(mockUserMgr, Mockito.never())
        .setPreference(Preference.UI_PDF_PAGE_SIZE, defaultCfg.getPageSize(), user.getUsername());
    assertEquals("success", rst);

    // setting size default
    defaultCfg.setSetPageSizeAsDefault(true);
    rst =
        exportController.export(
            createExportConfig(exportIds, exportNames, exportTypes, defaultCfg), errors, principal);
    Mockito.verify(mockUserMgr, Mockito.times(1))
        .setPreference(Preference.UI_PDF_PAGE_SIZE, defaultCfg.getPageSize(), user.getUsername());
    assertEquals("success", rst);
  }

  @Test
  public void testExportPdfAllUserRecords() throws Exception {
    mockMessageSource.addMessage(
        "pdfArchiving.submission.successMsg", Locale.getDefault(), "success");
    Mockito.when(principal.getName()).thenReturn("user1a");
    Mockito.when(mockUserMgr.getUserByUsername("user1a")).thenReturn(user);
    Mockito.when(bindingResult.hasErrors()).thenReturn(false);

    ExportDialogConfigDTO exportConfig = createExportConfigForUser(principal.getName());

    String rst = exportController.export(exportConfig, bindingResult, principal);

    Mockito.verify(exImportMgr, Mockito.times(1))
        .asyncExportAllUserRecordsToPdf(user, exportConfig.getExportConfig(), user);
    Mockito.verify(bindingResult, Mockito.times(1)).hasErrors();

    assertTrue(rst != null);
    assertEquals("success", rst);

    // word unsupported
    exportConfig.getExportConfig().setExportFormat(WORD);
    rst = exportController.export(exportConfig, bindingResult, principal);
    assertEquals("failure: unsupported", rst);
  }

  @Test
  public void testExportPdfOfOtherUsersRecordsRequiresAdminRole() throws Exception {
    final User other = TestFactory.createAnyUser("other_user");
    final User exporter = TestFactory.createAnyUser("exporter");

    when(principal.getName()).thenReturn("exporter");
    when(mockUserMgr.getUserByUsername("exporter")).thenReturn(exporter);
    when(mockUserMgr.getUserByUsername("other_user")).thenReturn(other);
    when(bindingResult.hasErrors()).thenReturn(false);
    Mockito.doThrow(new AuthorizationException("auth_error"))
        .when(exImportMgr)
        .assertExporterCanExportUsersWork(other, exporter);
    ExportDialogConfigDTO exportConfig = createExportConfigForUser(other.getUsername());

    // check that regular user can't export another's records
    String rst = exportController.export(exportConfig, bindingResult, principal);
    Mockito.verify(exImportMgr, never())
        .asyncExportAllUserRecordsToPdf(other, exportConfig.getExportConfig(), user);
    assertEquals("failure: auth_error", rst);
  }

  @Test
  public void testExportPdfOfGroupsRecords() throws Exception {
    final ExportDialogConfigDTO exportConfig = createExportConfigForGroup();
    final Group group = new Group();
    group.addMember(user, RoleInGroup.RS_LAB_ADMIN);
    group.setLabAdminViewAll(user, true);

    mockMessageSource.addMessage(
        "pdfArchiving.submission.successMsg", Locale.getDefault(), "success");
    when(principal.getName()).thenReturn("user1a");
    when(mockUserMgr.getUserByUsername("user1a")).thenReturn(user);
    when(groupManager.getGroup(1L)).thenReturn(group);
    when(grpPermUtils.userCanExportGroup(user, group)).thenReturn(true);

    exportController.export(exportConfig, bindingResult, principal);
    verify(exImportMgr, times(1)).asyncExportGroupToPdf(exportConfig.getExportConfig(), user, 1L);

    exportConfig.getExportConfig().setExportFormat(WORD);
    String rst = exportController.export(exportConfig, bindingResult, principal);
    assertEquals("failure: unsupported", rst);
  }

  @Test
  public void serverPathValidation() throws Exception {

    Validator validator = getValidator();
    ServerPath path = new ServerPath();
    path.setServerFilePath("");
    Set<ConstraintViolation<Object>> violations = validator.validate(path);
    assertTrue(violations.size() > 0);
    path.setServerFilePath("/fff'gggg");
    violations = validator.validate(path);
    assertTrue(violations.size() > 0);

    path.setServerFilePath("/fffgggg.png"); // not a zip
    violations = validator.validate(path);
    assertTrue(violations.size() > 0);

    String TOO_LONG_PATH = randomAlphabetic(252);
    path.setServerFilePath("/" + TOO_LONG_PATH + ".zip");
    violations = validator.validate(path);
    assertTrue(violations.size() > 0);
    String Max_PATH = randomAlphabetic(251) + ".zip"; // max path component on linux + 4 for .zip
    for (String validPath : toList("/abc.zip", "/a/b/c.zip", "/" + Max_PATH)) {
      path.setServerFilePath(validPath);
      violations = validator.validate(path);
      assertTrue("Path " + validPath + " should be valid", violations.size() == 0);
    }
  }

  private Validator getValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    return validator;
  }

  @Test
  public void displayPdfArgumentValidation() throws Exception {
    assertExceptionThrown(
        () -> exportController.displayPdf("", "any", principal, response),
        IllegalArgumentException.class);

    mockMessageSource.addMessage("errors.invalidstringformat", Locale.getDefault(), "format");
    assertExceptionThrown(
        () -> exportController.displayPdf("../../passwordfile.txt", "any", principal, response),
        IllegalArgumentException.class);

    mockMessageSource.addMessage("errors.invalidstringformat", Locale.getDefault(), "format");
    when(principal.getName()).thenReturn("user1a");
    when(mockUserMgr.getUserByUsername("user1a")).thenReturn(user);
    exportController.displayPdf("OkPDF.txt", "any", principal, response);
    verify(pdfUtils, times(1)).display("OkPDF.txt", user, response);
  }

  @Test
  public void depositArchiveValidateTooManyIds() throws Exception {
    // any or all of these causes too many exception if exceeds limit
    Long[] ids = ids(ExportController.maxIdsToProcess + 1);
    String[] types = types(ExportController.maxIdsToProcess);
    String[] names = names(ExportController.maxIdsToProcess);
    when(errors.hasErrors()).thenReturn(false);

    String msg =
        exportController.exportArchive(
            createExportArchiveConfig(ids, names, types, true), errors, request, principal);
    assertEquals("failure: toomany", msg);

    ids = ids(ExportController.maxIdsToProcess);
    types = types(ExportController.maxIdsToProcess + 1);
    msg =
        exportController.exportArchive(
            createExportArchiveConfig(ids, names, types, true), errors, request, principal);
    assertEquals("failure: toomany", msg);

    setupArchiveNotMadeExpectation();
  }

  private void setupArchiveNotMadeExpectation() {
    verify(exImportMgr, never())
        .asyncExportSelectionToArchive(
            Mockito.any(ExportSelection.class),
            Mockito.any(ArchiveExportConfig.class),
            Mockito.any(User.class),
            Mockito.any(URI.class),
            Mockito.any(PostArchiveCompletion.class));
  }

  private UserAppConfig setupMockAppCfg(Long appSetId, String appName) {
    UserAppConfig appCfg = createAnyAppWithConfigElements(user, appName);
    when(appCfgMgr.findByAppConfigElementSetId(appSetId))
        .thenReturn(Optional.of(appCfg.getAppConfigElementSets().iterator().next()));
    return appCfg;
  }

  @Test
  public void depositArchiveHappyCase() throws Exception {
    Long[] ids = ids(5);
    String[] types = types(5);
    String[] names = names(5);
    final Long appSetId = 1L;

    final ArchiveResult res = new ArchiveResult();
    res.setExportFile(new File("path"));

    // wrong app
    UserAppConfig slackCfg = setupMockAppCfg(appSetId, App.APP_SLACK);
    when(appCfgMgr.getByAppName(App.APP_DATAVERSE, user)).thenReturn(slackCfg);
    when(mockUserMgr.getUserByUsername("user1a")).thenReturn(user);
    when(principal.getName()).thenReturn("user1a");
    when(properties.getServerUrl()).thenReturn("http://www.rspace.com");

    String msg =
        exportController.exportArchive(
            createExportArchiveConfig(ids, names, types, true), errors, request, principal);
    Mockito.verify(depositHandler, never()).sendArchiveToRepository(any(), any(), any(), any());
    assertEquals("failure: badchoice", msg);

    UserAppConfig uac = setupMockAppCfg(appSetId, App.APP_DATAVERSE);
    when(appCfgMgr.getByAppName(uac.getApp().getName(), user)).thenReturn(uac);
    msg =
        exportController.exportArchive(
            createExportArchiveConfig(ids, names, types, true), errors, request, principal);
    Mockito.verify(depositHandler, times(1)).sendArchiveToRepository(any(), any(), any(), any());
    assertEquals("success", msg);
  }

  @Test
  public void checkStringMatchesPDFAndZipRegex() {
    for (int i = 0; i < 1000; i++) {
      String random = SecureStringUtils.getURLSafeSecureRandomString(10);
      String zip = random + ".zip";
      Matcher m = ExportImport.EXPORTED_ARCHIVE_NAME_PATTERN.matcher(zip);
      assertTrue(zip + " doesn't match", m.matches());
      zip = random + ".eln";
      Matcher m2 = ExportImport.EXPORTED_ARCHIVE_NAME_PATTERN.matcher(zip);
      assertTrue(zip + " doesn't match", m2.matches());
      zip = random + ".csv";
      Matcher m3 = ExportImport.EXPORTED_ARCHIVE_NAME_PATTERN.matcher(zip);
      assertTrue(zip + " doesn't match", m3.matches());
      String pdf = "export_" + random + ".pdf";
      Matcher m4 = ExportController.VALID_PDF_FILE_CHARS.matcher(pdf);
      assertTrue(pdf + " doesn't match", m4.matches());
    }
  }
}
