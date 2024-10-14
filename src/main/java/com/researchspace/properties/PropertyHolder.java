package com.researchspace.properties;

import com.researchspace.core.util.version.SemanticVersion;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * For holding properties that for some reason can't be injected into Controllers, and used in JSPs
 * as well.<br>
 * This class is created as SpringBean in BaseConfig for wiring into classes that need to use them.
 */
public class PropertyHolder implements IMutablePropertyHolder {
  @Value("${server.urls.prefix}")
  private String urlPrefix;

  @Value("${rs.customer.name}")
  private String customerName;

  @Value("${rs.customer.name.short}")
  private String customerNameShort;

  @Value("${user.signup}")
  private String userSignup;

  @Value("${netfilestores.enabled}")
  private String netFileStoresEnabled;

  @Value("${netfilestores.export.enabled}")
  private String netFileStoresExportEnabled;

  @Value("${offline.button.visible}")
  private String offlineButtonVisible;

  @Value("${archive.folder.location}")
  private String exportFolderLocation;

  @Value("${deployment.standalone}")
  private String standalone;

  @Value("${deployment.cloud}")
  private String cloud;

  @Value("${sysadmin.delete.user}")
  private String deleteUser;

  @Value("${sysadmin.delete.user.deleteResourcesImmediately}")
  @Getter
  private Boolean deleteUserResourcesImmediately;

  @Value("${sysadmin.errorfile.path}")
  private String absPathToErrorLog;

  @Value("${sysadmin.rspace.support.email}")
  private String rspaceSupportEmail;

  @Value("${ui.bannerImage.path}")
  private String bannerImagePath;

  @Value("${ui.bannerImage.url}")
  @Getter
  private String bannerImageLink;

  @Value("${netfilestores.login.directory.option}")
  private String loginDirectoryOption;

  /**
   * This is an example of spring SpEL (spring expression language) to set this variable to a
   * default value if no property is defined, in this case it's an empty map. See documentation
   * here: https://www.baeldung.com/spring-value-annotation and
   * https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html
   */
  @Value("#{${ui.footer.urls: {:}} ?: {:}}")
  @Getter
  private Map<String, String> uiFooterUrls;

  @Value("${ui.bannerImage.loggedOutUrl}")
  @Getter
  private String bannerImageLoggedOutLink;

  @Value("${ldap.enabled}")
  private String ldapEnabled;

  @Value("${ror.enabled}")
  private String rorEnabled;

  @Value("${ldap.authentication.enabled}")
  private String ldapAuthenticationEnabled;

  @Value("${ldap.authentication.sidVerification.enabled}")
  private String ldapSidVerificationEnabled;

  @Value("${files.maxUploadSize}")
  private String maxUploadSize;

  @Value("${rsversion}")
  private String rsversion;

  @Value("${onedrive.client.id}")
  private String oneDriveClientId;

  @Value("${onedrive.redirect}")
  private String oneDriveRedirect;

  @Value("${slack.client.id}")
  private String slackClientId;

  @Value("${orcid.client.id}")
  private String orcidClientId;

  @Value("${github.client.id}")
  private String githubClientId;

  @Value("${user.signup.captcha.enabled}")
  private String signupCaptchaEnabled;

  @Value("${user.signup.captcha.site.key}")
  private String signupCaptchaSiteKey;

  @Value("${profile.email.editable}")
  private String profileEmailEditable;

  @Value("${profile.firstlastname.editable}")
  private String profileNameEditable;

  @Value("${profile.hiding.enabled}")
  private String profileHidingEnabled;

  @Value("${deployment.sso.ssoInfoVariant}")
  private String ssoInfoVariant;

  @Value("${deployment.sso.logout.url}")
  private String ssoLogoutUrl;

  @Value("${deployment.sso.idp.logout.url}")
  private String ssoIdpLogoutUrl;

  @Value("${deployment.sso.adminLogin.enabled}")
  private Boolean ssoAdminLoginEnabled;

  @Value("${deployment.sso.selfDeclarePI.enabled}")
  private boolean ssoSelfDeclarePiEnabled;

  @Value("${deployment.sso.adminEmail}")
  private String ssoAdminEmail;

  @Value("${errorPage.showStackTrace}")
  private String showStackTraceInErrorPageEnabled;

  @Value("${picreateGroupOnSignup.enabled}")
  private String picreateGroupOnSignupEnabled;

  @Value("${slow.request.time:5000}")
  private Integer slowRequestTimeThreshold;

  @Value("${aspose.cacheConverted}")
  private String asposeCacheConverted;

  @Value("${msoffice.wopi.enabled}")
  private boolean msOfficeEnabled;

  @Value("${collabora.wopi.enabled}")
  private boolean collaboraEnabled;

  @Value("${importArchiveFromServer.enabled}")
  private String importArchiveFromServerEnabled;

  @Value("${rs.filestore}")
  private String fileStoreType;

  @Value("${sysadmin.limitedIpAddresses.enabled}")
  private String whiteListedIpsEnabled;

  @Value("${user.signup.signupCode}")
  private String userSignupCode;

  @Value("${api.beta.enabled}")
  private String apiBetaEnabled;

  @Value("${license.exceeded.custom.message}")
  private String licenseExceededCustomMessage;

  @Value("${login.customLoginContent}")
  private String customLoginContent;

  @Value("${signup.customSignupContent}")
  private String customSignupContent;

  @Value("${jwt.secret:}")
  private String jwtSecret;

  private Key jwtKey = null;

  @Value("${jove.api.url}")
  private String joveApiUrl;

  @Value("${jove.api.key}")
  private String joveApiKey;

  @Value("${dryad.base.url}")
  private String dryadBaseUrl;

  @Value("${zenodo.url}")
  private String zenodoApiUrl;

  @Value("${dcd.base.url}")
  @Getter
  @Setter
  private String digitalCommonsDataBaseUrl;

  @Value("${aspose.enabled:true}")
  private String asposeEnabled;

  @Override
  public Key getJwtKey() {
    if (this.jwtKey == null) {
      if (jwtSecret.isEmpty()) { // Generate the key
        this.jwtKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
      } else {
        this.jwtKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
      }
    }

    return this.jwtKey;
  }

  @Value("${username.length.min:6}")
  private Integer minUsernameLength;

  public void setWhiteListedIpsEnabled(String whiteListedIpsEnabled) {
    this.whiteListedIpsEnabled = whiteListedIpsEnabled;
  }

  @Autowired ResourceLoader resourceLoader;

  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  Logger log = LoggerFactory.getLogger(getClass());

  public String getBannerImagePath() {
    return bannerImagePath;
  }

  @Override
  public String getBannerImageName() {
    if (!StringUtils.isEmpty(bannerImagePath)) {
      String[] items = bannerImagePath.split("/");
      return items[items.length - 1];
    } else {
      return "unknown";
    }
  }

  public void setBannerImagePath(String bannerImagePath) {
    Resource resource = resourceLoader.getResource(bannerImagePath);
    if (!resource.exists()) {
      log.warn("Couldn't get resource from {}", bannerImagePath);
    }
    this.bannerImagePath = bannerImagePath;
  }

  @Value("${analytics.enabled}")
  private String analyticsEnabled;

  @Value("${analytics.server.type}")
  private String analyticsServerType;

  public String getAnalyticsEnabled() {
    return analyticsEnabled;
  }

  public String getAnalyticsServerType() {
    return analyticsServerType;
  }

  public void setAnalyticsEnabled(String analyticsEnabled) {
    this.analyticsEnabled = analyticsEnabled;
  }

  @Override
  public String getCustomLoginContent() {
    return customLoginContent;
  }

  @Override
  public String getCustomSignupContent() {
    return customSignupContent;
  }

  @Override
  public boolean isLoginDirectoryOption() {
    return Boolean.parseBoolean(loginDirectoryOption);
  }

  @Override
  public String getJoveApiUrl() {
    return joveApiUrl;
  }

  @Override
  public void setJoveApiUrl(String joveApiUrl) {
    this.joveApiUrl = joveApiUrl;
  }

  @Override
  public String getJoveApiKey() {
    return this.joveApiKey;
  }

  @Override
  public void setJoveApiKey(String joveApiKey) {
    this.joveApiKey = joveApiKey;
  }

  public String getDryadBaseUrl() {
    return this.dryadBaseUrl;
  }

  @Override
  public String getZenodoApiUrl() {
    return this.zenodoApiUrl;
  }

  public Integer getMinUsernameLength() {
    return minUsernameLength;
  }

  @Override
  public void setMinUsernameLength(Integer minUsernameLength) {
    this.minUsernameLength = minUsernameLength;
  }

  @Override
  public String getLicenseExceededCustomMessage() {
    return licenseExceededCustomMessage == null ? "" : licenseExceededCustomMessage;
  }

  @Override
  public void setLicenseExceededCustomMessage(String message) {
    licenseExceededCustomMessage = message;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getDeleteUser()
   */
  @Override
  public String getDeleteUser() {
    return deleteUser;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getCloud()
   */
  @Override
  public String getCloud() {
    return cloud;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#isCloud()
   */
  @Override
  public boolean isCloud() {
    return Boolean.parseBoolean(cloud);
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#isSSO()
   */
  @Override
  public Boolean isSSO() {
    return !isStandalone();
  }

  @Override
  public String getSSOInfoVariant() {
    return ssoInfoVariant;
  }

  @Override
  public String getSSOLogoutUrl() {
    return ssoLogoutUrl;
  }

  @Override
  public String getSSOIdpLogoutUrl() {
    return ssoIdpLogoutUrl;
  }

  @Override
  public Boolean getSSOAdminLoginEnabled() {
    return ssoAdminLoginEnabled;
  }

  @Override
  public boolean isSSOSelfDeclarePiEnabled() {
    return ssoSelfDeclarePiEnabled;
  }

  @Override
  public void setSSOSelfDeclarePiEnabled(Boolean enabled) {
    ssoSelfDeclarePiEnabled = enabled;
  }

  @Override
  public String getSSOAdminEmail() {
    return ssoAdminEmail;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#isStandalone()
   */
  @Override
  public Boolean isStandalone() {
    return Boolean.parseBoolean(standalone);
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getUrlPrefix()
   */
  @Override
  public String getUrlPrefix() {
    return urlPrefix;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getStandalone()
   */
  @Override
  public String getStandalone() {
    return standalone;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getExportFolderLocation()
   */
  @Override
  public String getExportFolderLocation() {
    return exportFolderLocation;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getOfflineButtonVisible()
   */
  @Override
  public String getOfflineButtonVisible() {
    return offlineButtonVisible;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getNetFileStoresEnabled()
   */
  @Override
  public String getNetFileStoresEnabled() {
    return netFileStoresEnabled;
  }

  @Override
  public String getNetFileStoresExportEnabled() {
    return netFileStoresExportEnabled;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getUserSignup()
   */
  @Override
  public String getUserSignup() {
    return userSignup;
  }

  /* (non-Javadoc)
   * @see com.researchspace.properties.IPropertyHolder#getEmailUrlPrefix()
   */
  @Override
  public String getServerUrl() {
    return urlPrefix;
  }

  @Override
  public String getCustomerName() {
    return customerName;
  }

  @Override
  public String getCustomerNameShort() {
    return customerNameShort;
  }

  @Override
  public String getErrorLogFile() {
    return absPathToErrorLog;
  }

  @Override
  public String getRSpaceSupportEmail() {
    return rspaceSupportEmail;
  }

  public boolean isAsposeEnabled() {
    return Boolean.parseBoolean(asposeEnabled);
  }

  /*
   * ============
   * for testing
   */

  @Override
  public void setStandalone(String standalone) {
    this.standalone = standalone;
  }

  @Override
  public void setCloud(String cloud) {
    this.cloud = cloud;
  }

  @Override
  public void setNetFileStoresEnabled(String netFileStoresEnabled) {
    this.netFileStoresEnabled = netFileStoresEnabled;
  }

  /** Should be a boolean string. */
  @Override
  public String getLdapEnabled() {
    return ldapEnabled;
  }

  @Override
  public String getRorEnabled() {
    return rorEnabled;
  }

  @Override
  public void setLdapEnabled(String enabled) {
    this.ldapEnabled = enabled;
  }

  public void setRoREnabled(String rorEnabled) {
    this.rorEnabled = rorEnabled;
  }

  @Override
  public boolean isLdapAuthenticationEnabled() {
    return Boolean.parseBoolean(ldapAuthenticationEnabled);
  }

  @Override
  public void setLdapAuthenticationEnabled(String enabled) {
    ldapAuthenticationEnabled = enabled;
  }

  @Override
  public boolean isLdapSidVerificationEnabled() {
    return Boolean.parseBoolean(ldapSidVerificationEnabled);
  }

  @Override
  public void setLdapSidVerificationEnabled(String enabled) {
    ldapSidVerificationEnabled = enabled;
  }

  @Override
  public long getMaxUploadSize() {
    return Long.parseLong(maxUploadSize);
  }

  @Override
  public void setMaxUploadSize(long maxSize) {
    this.maxUploadSize = maxSize + "";
  }

  @Override
  public String getVersionMessage() {
    return rsversion;
  }

  @Override
  public SemanticVersion getVersion() {
    return new SemanticVersion(rsversion);
  }

  @Override
  public String getDescription() {
    return "RSpace version";
  }

  @Override
  public Boolean isUserSignup() {
    return Boolean.parseBoolean(userSignup);
  }

  public void setUserSignup(String booleanValue) {
    this.userSignup = booleanValue;
  }

  @Override
  public String getOneDriveClientId() {
    return oneDriveClientId;
  }

  @Override
  public String getOneDriveRedirect() {
    return oneDriveRedirect;
  }

  @Override
  public String getSlackClientId() {
    return slackClientId;
  }

  @Override
  public String getOrcidClientId() {
    return orcidClientId;
  }

  @Override
  public String getGithubClientId() {
    return githubClientId;
  }

  @Override
  public String getSignupCaptchaEnabled() {
    return signupCaptchaEnabled;
  }

  @Override
  public String getSignupCaptchaSiteKey() {
    return signupCaptchaSiteKey;
  }

  @Override
  public boolean isProfileEmailEditable() {
    return Boolean.parseBoolean(profileEmailEditable);
  }

  @Override
  public boolean isProfileNameEditable() {
    return Boolean.parseBoolean(profileNameEditable);
  }

  @Override
  public boolean isProfileHidingEnabled() {
    return Boolean.parseBoolean(profileHidingEnabled);
  }

  @Override
  public void setProfileEmailEditable(boolean editable) {
    this.profileEmailEditable = Boolean.toString(editable);
  }

  @Override
  public void setProfileNameEditable(boolean editable) {
    this.profileNameEditable = Boolean.toString(editable);
  }

  @Override
  public boolean isShowStackTraceInErrorPageEnabled() {
    return Boolean.parseBoolean(showStackTraceInErrorPageEnabled);
  }

  @Override
  public boolean isPicreateGroupOnSignupEnabled() {
    return Boolean.parseBoolean(picreateGroupOnSignupEnabled);
  }

  @Override
  public void setPicreateGroupOnSignupEnabled(Boolean enabled) {
    picreateGroupOnSignupEnabled = enabled.toString();
  }

  @Override
  public Integer getSlowLogThreshold() {
    return slowRequestTimeThreshold;
  }

  @Override
  public boolean isAsposeCachingEnabled() {
    return Boolean.parseBoolean(asposeCacheConverted);
  }

  @Override
  public void setExportFolderLocation(String absolutePath) {
    this.exportFolderLocation = absolutePath;
  }

  public boolean isMsOfficeEnabled() {
    return msOfficeEnabled;
  }

  public void setMsOfficeEnabled(boolean msOfficeEnabled) {
    this.msOfficeEnabled = msOfficeEnabled;
  }

  public boolean isCollaboraEnabled() {
    return collaboraEnabled;
  }

  public void setCollaboraEnabled(boolean collaboraEnabled) {
    this.collaboraEnabled = collaboraEnabled;
  }

  @Override
  public boolean isImportArchiveFromServerEnabled() {
    return Boolean.parseBoolean(importArchiveFromServerEnabled);
  }

  @Override
  public String getFileStoreType() {
    return fileStoreType;
  }

  public void setFileStoreType(String fileStoreType) {
    this.fileStoreType = fileStoreType;
  }

  @Override
  public boolean isSysadminWhiteListingEnabled() {
    return Boolean.parseBoolean(whiteListedIpsEnabled);
  }

  @Override
  public String getUserSignupCode() {
    return userSignupCode;
  }

  @Override
  public void setUserSignupCode(String userSignupCode) {
    this.userSignupCode = userSignupCode;
  }

  @Override
  public boolean isApiBetaEnabled() {
    return Boolean.parseBoolean(apiBetaEnabled);
  }

  @Override
  public void setCustomLoginContent(String customLoginContent) {
    this.customLoginContent = customLoginContent;
  }

  @Override
  public String toString() {
    return "PropertyHolder [urlPrefix="
        + urlPrefix
        + ", userSignup="
        + userSignup
        + ", netFileStoresEnabled="
        + netFileStoresEnabled
        + ", offlineButtonVisible="
        + offlineButtonVisible
        + ", exportFolderLocation="
        + exportFolderLocation
        + ", standalone="
        + standalone
        + ", cloud="
        + cloud
        + ", deleteUser="
        + deleteUser
        + ", absPathToErrorLog="
        + absPathToErrorLog
        + ", rspaceSupportEmail="
        + rspaceSupportEmail
        + ", bannerImagePath="
        + bannerImagePath
        + ", ldapEnabled="
        + ldapEnabled
        + ", rorEnabled="
        + rorEnabled
        + ", maxUploadSize="
        + maxUploadSize
        + ", rsversion="
        + rsversion
        + ", oneDriveClientId="
        + oneDriveClientId
        + ", oneDriveRedirect="
        + oneDriveRedirect
        + ", slackClientId="
        + slackClientId
        + ", orcidClientId="
        + orcidClientId
        + ", githubClientId="
        + githubClientId
        + ", analyticsEnabled="
        + analyticsEnabled
        + ", loginDirectoryOption="
        + loginDirectoryOption
        + "]";
  }
}
