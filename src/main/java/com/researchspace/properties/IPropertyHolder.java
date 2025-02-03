package com.researchspace.properties;

import com.researchspace.core.util.version.Versionable;
import java.security.Key;

/**
 * Abstracts property and environment settings which influence application behaviour. <br>
 * This interface must provide only read-only methods for use in production classes. <br>
 * For testing, use the {@link IMutablePropertyHolder} interface to simulate setting properties.
 */
public interface IPropertyHolder extends Versionable {

  /**
   * Getter for sysadmin.delete.user property
   *
   * @return the deleteUser
   */
  String getDeleteUser();

  /**
   * @return sysadmin.delete.user.deleteResourcesImmediately property
   */
  Boolean getDeleteUserResourcesImmediately();

  /**
   * @return the cloud
   */
  String getCloud();

  /**
   * Returns <code>true</code> if <code>deployment.cloud</code> property is <code>true</code>.
   *
   * @return
   */
  boolean isCloud();

  /**
   * Returns <code>true</code> if <code>deployment.standalone</code> property is <code>false</code>.
   *
   * @return
   */
  Boolean isSSO();

  /**
   * @return variant of ssoInfo page to be displayed
   */
  String getSSOInfoVariant();

  /**
   * @return address to redirect SSO user after logout
   */
  String getSSOLogoutUrl();

  /**
   * @return address which completely logs user out of their SSO IDP
   */
  String getSSOIdpLogoutUrl();

  /**
   * Returns boolean value of <code>deployment.sso.adminLogin.enabled</code> property.
   *
   * @return
   */
  Boolean getSSOAdminLoginEnabled();

  /**
   * Returns boolean value of <code>deployment.sso.selfDeclarePI.enabled</code> property.
   *
   * @return
   */
  boolean isSSOSelfDeclarePiEnabled();

  /**
   * @return email address of administrator of single-sign-on system
   */
  String getSSOAdminEmail();

  /**
   * Returns <code>true</code> if <code>deployment.standalone</code> property is <code>true</code>.
   *
   * @return
   */
  Boolean isStandalone();

  /**
   * Returns <code>true</code> if <code>user.signup</code> property is <code>true</code>.
   *
   * @return
   */
  Boolean isUserSignup();

  /**
   * @return the urlPrefix
   */
  String getUrlPrefix();

  /**
   * @return the standalone
   */
  String getStandalone();

  /**
   * Gets location of folder where archive files(XML, HTML) are kept on the server
   *
   * @return
   */
  String getExportFolderLocation();

  String getOfflineButtonVisible();

  String getNetFileStoresEnabled();

  boolean isNetFileStoresEnabled();

  String getNetFileStoresExportEnabled();

  String getUserSignup();

  String getServerUrl();

  /** Name of the company/institution for which this RSpace instance is deployed */
  String getCustomerName();

  /** Short name of the company/institution for which this RSpace instance is deployed */
  String getCustomerNameShort();

  /**
   * Get absolute path to latest error log file.
   *
   * @return
   */
  String getErrorLogFile();

  /**
   * Get an email address for RSpace support.
   *
   * @return
   */
  String getRSpaceSupportEmail();

  /**
   * Manages whether backend-end Segment.io and front-end Segment.io, Google analytics are enabled
   */
  String getAnalyticsEnabled();

  String getBannerImagePath();

  String getLdapEnabled();

  String getRorEnabled();

  boolean isLdapAuthenticationEnabled();

  boolean isLdapSidVerificationEnabled();

  long getMaxUploadSize();

  /**
   * Convenience method to get banner image name from path
   *
   * @return
   */
  String getBannerImageName();

  /** OneDrive integration properties */
  String getOneDriveClientId();

  String getOneDriveRedirect();

  /** Slack integration property */
  String getSlackClientId();

  /** ORCID integration property */
  String getOrcidClientId();

  /** GitHub integration property */
  String getGithubClientId();

  /** Signup captcha property */
  String getSignupCaptchaEnabled();

  String getSignupCaptchaSiteKey();

  boolean isProfileEmailEditable();

  boolean isProfileNameEditable();

  boolean isProfileHidingEnabled();

  boolean isShowStackTraceInErrorPageEnabled();

  /**
   * @return
   * @see https://researchspace.atlassian.net/browse/RSPAC-1336
   */
  boolean isPicreateGroupOnSignupEnabled();

  Integer getSlowLogThreshold();

  boolean isAsposeCachingEnabled();

  boolean isMsOfficeEnabled();

  boolean isCollaboraEnabled();

  /**
   * Should be a URL to link to from the main banner page
   *
   * @return a URL
   */
  String getBannerImageLink();

  /**
   * Whether import of XML archives located on server is supported.
   *
   * @return
   */
  boolean isImportArchiveFromServerEnabled();

  /**
   * Filestore implementation used by this RSpace instance.
   *
   * @return 'LOCAL' for standard filestore, but may be other.
   */
  String getFileStoreType();

  /**
   * Boolean value of the property <code>sysadmin.limitedIpAddresses.enabled</code>
   *
   * @return
   */
  boolean isSysadminWhiteListingEnabled();

  /**
   * Returns possibly empty usersignup code if user-signup requires a secret code to match this one
   * on server. RSPAC-1796
   *
   * @return
   */
  String getUserSignupCode();

  /**
   * Whether beta-api is enabled
   *
   * @return
   */
  boolean isApiBetaEnabled();

  Key getJwtKey();

  /**
   * Optional custom message to append to license-exceeded error message, see RSPAC-2118
   *
   * @return An empty string if not set, or the message.
   */
  String getLicenseExceededCustomMessage();

  /**
   * Gets minimum username length
   *
   * @return
   */
  Integer getMinUsernameLength();

  String getCustomLoginContent();

  String getCustomSignupContent();

  boolean isLoginDirectoryOption();

  String getJoveApiUrl();

  String getJoveApiKey();

  String getDryadBaseUrl();

  String getZenodoApiUrl();

  String getDigitalCommonsDataBaseUrl();

  String getFieldmarkBaseUrl();

  boolean isAsposeEnabled();
}
