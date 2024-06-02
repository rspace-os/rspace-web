package com.researchspace.webapp.controller;

import static com.researchspace.service.IntegrationsHandler.EVERNOTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.session.SessionAttributeUtils.USER_INFO;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.core.util.ResponseUtil;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.DefaultBreadcrumbGenerator;
import com.researchspace.model.record.PermissionsAdaptable;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.AuditManager;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.OrganisationManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.session.UserSessionTracker;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/**
 * Base class for controllers containing code/ services common to all or most controllers. <br>
 * Currently this class is derived from an AppFuse base-class and contains some methods to be
 * removed (get/set Cancel/success view) after refactoring.
 */
public abstract class BaseController implements ServletContextAware {

  protected final transient Logger log = LoggerFactory.getLogger(getClass());
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  protected static final String UTF_8 = "UTF-8";
  protected @Autowired UserManager userManager;
  protected @Autowired GroupManager groupManager;
  protected @Autowired RecordManager recordManager;
  protected @Autowired BaseRecordManager baseRecordManager;
  protected @Autowired BaseRecordAdaptable recordAdapter;
  protected @Autowired FolderManager folderManager;
  protected @Autowired LicenseService licenseService;
  protected @Autowired OperationFailedMessageGenerator authGenerator;
  protected @Autowired AuditTrailService auditService;
  protected @Autowired AuditManager auditManager;
  protected @Autowired IPermissionUtils permissionUtils;
  protected @Autowired IControllerInputValidator inputValidator;

  protected void setInputValidator(IControllerInputValidator inputValidator) {
    this.inputValidator = inputValidator;
  }

  protected @Autowired IPropertyHolder properties;
  protected @Autowired OrganisationManager organisationManager;
  protected @Autowired @Qualifier("compositeFileStore") FileStore fileStore;
  protected @Autowired IntegrationsHandler integrationsHandler;
  @Autowired PaginationSettingsPreferences paginationSettingsPreferences;

  protected String cancelView;
  protected String successView;
  protected ServletContext servletContext;

  protected @Autowired MessageSourceUtils messages;

  protected @Autowired ApplicationEventPublisher publisher;

  public void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  protected BreadcrumbGenerator breadcrumbGenerator = new DefaultBreadcrumbGenerator();
  protected @Autowired IControllerExceptionHandler exceptionHandler;

  @ExceptionHandler()
  public ModelAndView handleExceptions(
      HttpServletRequest request, HttpServletResponse response, Exception e) {
    return exceptionHandler.handleExceptions(request, response, e);
  }

  public void setUserManager(UserManager userManager) {
    this.userManager = userManager;
  }

  public void setGroupManager(GroupManager groupManager) {
    this.groupManager = groupManager;
  }

  public void setRecordManager(RecordManager recordManager) {
    this.recordManager = recordManager;
  }

  public void setBaseRecordMgr(BaseRecordManager baseRecordManager) {
    this.baseRecordManager = baseRecordManager;
  }

  public void setLicenseService(LicenseService licenseService) {
    this.licenseService = licenseService;
  }

  public void setFileStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  public void setPermissionUtils(IPermissionUtils permUtils) {
    this.permissionUtils = permUtils;
  }

  public void setAuditService(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  public void setRecordAdapter(BaseRecordAdaptable recordAdapter) {
    this.recordAdapter = recordAdapter;
  }

  public void setFolderManager(FolderManager folderManager) {
    this.folderManager = folderManager;
  }

  public void setAuditService(AuditTrailService auditService) {
    this.auditService = auditService;
  }

  /**
   * Convenience method for getting a i18n key's value with default Locale.
   *
   * @param msgKey
   * @return
   */
  public String getText(String msgKey) {
    return messages.getMessage(msgKey);
  }

  /**
   * Convenient method for getting a i18n key's value with a single string argument and default
   * locale.
   *
   * @param msgKey
   * @param arg
   * @return
   */
  public String getText(String msgKey, String arg) {
    return getText(msgKey, new Object[] {arg});
  }

  /**
   * Convenience method for getting a i18n key's value with arguments and default locale.
   *
   * @param msgKey
   * @param args
   * @return
   */
  public String getText(String msgKey, Object[] args) {
    return messages.getMessage(msgKey, args);
  }

  /** Convenience method for getting a "resource not found" message. */
  public String getResourceNotFoundMessage(String resourceType, Long id) {
    return messages.getResourceNotFoundMessage(resourceType, id);
  }

  /**
   * Convenience method to return an {@link ErrorList} object for failed Ajax requests.
   *
   * @param msgKey
   * @param args
   * @return Returns an {@link ErrorList} with a single message.
   */
  public ErrorList getErrorListFromMessageCode(String msgKey, Object... args) {
    String message = getText(msgKey, args);
    return ErrorList.of(message);
  }

  public final void setCancelView(String cancelView) {
    this.cancelView = cancelView;
  }

  public final void setSuccessView(String successView) {
    this.successView = successView;
  }

  public final String getCancelView() {
    // Default to successView if cancelView is invalid
    if (this.cancelView == null || this.cancelView.length() == 0) {
      return this.successView;
    }
    return this.cancelView;
  }

  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  protected ServletContext getServletContext() {
    return servletContext;
  }

  /**
   * Checks whether given BaseRecord id points to a record
   *
   * @param id of a BaseRecord
   * @return if id belongs to a record
   */
  protected boolean isRecord(Long id) {
    return recordManager.exists(id);
  }

  /** Gets tracker of currently logged in users */
  protected UserSessionTracker getCurrentActiveUsers() {
    return (UserSessionTracker) servletContext.getAttribute(UserSessionTracker.USERS_KEY);
  }

  protected User getUserByUsername(String name) {
    return userManager.getUserByUsername(name);
  }

  /**
   * Utility method to assert that an object access is authorised
   *
   * @param user
   * @param objectToCheck
   * @param permType
   * @throws AuthorizationException if operation not allowed
   */
  protected void assertAuthorisation(User user, BaseRecord objectToCheck, PermissionType permType) {
    if (objectToCheck == null) {
      throwAuthException(null);
    }
    if (!isRecordAccessPermitted(user, objectToCheck, permType)) {
      throwAuthException(objectToCheck);
    }
  }

  protected boolean isRecordAccessPermitted(
      User user, BaseRecord objectToCheck, PermissionType permType) {
    return permissionUtils.isRecordAccessPermitted(user, objectToCheck, permType);
  }

  /**
   * Use this method to ensure user is a System Admins
   *
   * @param subject
   * @throws AuthorizationException if doesn't have Role.SYSTEM_ROLE
   */
  protected void assertUserIsSysAdmin(User subject) {
    if (!subject.hasRole(Role.SYSTEM_ROLE)) {
      throw new AuthorizationException(
          getText("system.unauthorized.userrole", new Object[] {subject.getFullName()}));
    }
  }

  private void throwAuthException(PermissionsAdaptable objectToCheck) {
    Long id =
        (objectToCheck == null) ? Long.valueOf(-1L) : objectToCheck.getPermissionsAdapter().getId();
    throw new AuthorizationException(getResourceNotFoundMessage("Document", id));
  }

  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  /**
   * ALternative method for use when a Spring ResponseEntity is used with an {@link HttpHeaders}
   * object.
   *
   * @param timeInSecondsToCache
   * @param lastModified last modified date of the object.
   * @param headers
   */
  protected void setCacheTimeInBrowser(
      int timeInSecondsToCache, Date lastModified, HttpHeaders headers) {
    headers.add(ResponseUtil.CACHE_CONTROL_HDR, "max-age=" + timeInSecondsToCache);
    if (lastModified != null) {
      headers.setLastModified(lastModified.getTime());
    }
  }

  /**
   * Boolean test for whether a list of {@link MultipartFile} has at least 1 file with non-empty
   * content.
   *
   * @param uploaded A list of {@link MultipartFile} (e.g. from multiple file upload)
   * @return <code>true</code> if there is &gt;= 1 file with non-empty content.
   */
  protected boolean isFileUploaded(List<MultipartFile> uploaded) {
    return !uploaded.isEmpty() && (uploaded.size() >= 1 && isUploadedFileNonEmpty(uploaded.get(0)));
  }

  /**
   * Boolean test for whether a {@link MultipartFile} has with non-empty content.
   *
   * @param uploaded a {@link MultipartFile}
   * @return <code>true</code> if the file has non-empty content.
   */
  protected boolean isUploadedFileNonEmpty(MultipartFile uploaded) {
    return uploaded.getSize() > 0;
  }

  /**
   * Creates and stores a {@link ProgressMonitor} in the session
   *
   * @param progressType - an identifier to distinguish this monitor from possible others in the
   *     session. The UI must use this to retrieve the {@link ProgressMonitor}
   * @param totalWorkUnits total units of work allocated
   * @param description an initial message
   * @param session the user's {@link HttpSession}
   * @return a newly created {@link ProgressMonitor}
   */
  protected ProgressMonitor createProgressMonitor(
      String progressType, int totalWorkUnits, String description, HttpSession session) {
    ProgressMonitor progress = new ProgressMonitorImpl(totalWorkUnits, description);
    session.setAttribute(progressType, progress);
    return progress;
  }

  protected HistoricalEvent createGenericEvent(User user, Object object, AuditAction create) {
    return new GenericEvent(user, object, create);
  }

  /**
   * Updates user object in session that is used for display on many pages.
   *
   * @param user
   * @param session
   */
  protected void updateSessionUser(User user, HttpSession session) {
    session.setAttribute(USER_INFO, user.toPublicInfo());
  }

  /**
   * checks if user was notified to refresh their permission cache, if they were, then returns
   * refreshed user.
   */
  protected User getUserWithRefreshedPermissions(User user) {
    if (permissionUtils.refreshCacheIfNotified()) {
      return userManager.getUserByUsername(user.getUsername(), true);
    }
    return user;
  }

  // see RSPAC-1029
  protected static boolean isValidSettingsKey(String settingsKey) {
    return !isEmpty(settingsKey) && settingsKey.matches("\\w+");
  }

  /**
   * Tests if string is blank, empty or null
   *
   * @param str
   * @return
   */
  protected boolean isInputStringBlank(String str) {
    return StringUtils.isBlank(str);
  }

  protected List<IntegrationInfo> getExternalMessagingIntegrationInfos(User user) {
    List<IntegrationInfo> extCommsInfo = new ArrayList<>();
    IntegrationInfo slack =
        integrationsHandler.getIntegration(user, IntegrationsHandler.SLACK_APP_NAME);
    IntegrationInfo msteams =
        integrationsHandler.getIntegration(user, IntegrationsHandler.MSTEAMS_APP_NAME);
    if (slack != null) {
      extCommsInfo.add(slack);
    }
    if (msteams != null) {
      extCommsInfo.add(msteams);
    }
    return extCommsInfo;
  }

  protected Object getUserPreferenceValue(User user, Preference pref) {
    UserPreference userPref = userManager.getPreferenceForUser(user, pref);
    return userPref == null ? null : userPref.getValue();
  }

  protected Boolean isProtocolsIOEnabled(User subject) {
    IntegrationInfo info = integrationsHandler.getIntegration(subject, PROTOCOLS_IO_APP_NAME);
    return info.isAvailable() && info.isEnabled();
  }

  protected Boolean isEvernoteEnabled(User subject) {
    IntegrationInfo info = integrationsHandler.getIntegration(subject, EVERNOTE_APP_NAME);
    return info.isAvailable() && info.isEnabled();
  }

  protected Boolean isAsposeEnabled() {
    return properties.isAsposeEnabled();
  }

  /*
   * Compares provided resultsPerPage setting with user's preference,
   * and either applies or updates the preference
   */
  void updateResultsPerPageProperty(
      User user, PaginationCriteria<?> pgCrit, Preference preference) {
    paginationSettingsPreferences.updateResultsPerPageProperty(user, pgCrit, preference);
  }

  /*
   * for testing
   */

  public void setMessageSource(MessageSourceUtils messages) {
    this.messages = messages;
  }

  protected boolean isDMPEnabled(User currUser) {
    IntegrationInfo dmpToolInfo =
        integrationsHandler.getIntegration(currUser, IntegrationsHandler.DMPTOOL_APP_NAME);
    boolean dmpToolIsEnabled =
        dmpToolInfo != null && dmpToolInfo.isEnabled() && dmpToolInfo.isAvailable();
    if (dmpToolIsEnabled) {
      return true;
    }
    IntegrationInfo argosInfo =
        integrationsHandler.getIntegration(currUser, IntegrationsHandler.ARGOS_APP_NAME);
    boolean argosIsEnabled = argosInfo != null && argosInfo.isEnabled() && argosInfo.isAvailable();
    if (argosIsEnabled) {
      return true;
    }
    IntegrationInfo dmpOnlineInfo =
        integrationsHandler.getIntegration(currUser, IntegrationsHandler.DMPONLINE_APP_NAME);
    boolean dmpOnlineIsEnabled =
        dmpOnlineInfo != null && dmpOnlineInfo.isEnabled() && dmpOnlineInfo.isAvailable();
    if (dmpOnlineIsEnabled) {
      return true;
    }
    return false;
  }

  protected ResponseEntity<Object> getAjaxMessageResponseEntity(
      HttpStatus status, BindingResult errors) {
    String errorMsg =
        errors.getAllErrors().stream()
            .map(err -> messages.getMessage(err))
            .collect(Collectors.joining(","));
    return getAjaxMessageResponseEntity(status, errorMsg);
  }

  protected ResponseEntity<Object> getAjaxMessageResponseEntity(
      HttpStatus status, String errorMsg) {
    return ResponseEntity.status(status).body(new AjaxMessageObject(errorMsg));
  }
}
