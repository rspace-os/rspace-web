package com.researchspace.webapp.integrations.wopi;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.properties.IPropertyHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Utils class to hold common methods between msoffice and collabora controllers */
@Component
public class WopiUtils {

  @Autowired private IPropertyHolder propertyHolder;

  @Value("${msoffice.wopi.redirect.server.url}")
  private String msOfficeWopiServerUrlOverride;

  /**
   * Get the wopi server files url for the respective wopi client
   *
   * @param mediaFile media file the get the link for
   * @return the wopi server files url as a string
   */
  public String getWopiServerFilesUrl(EcatMediaFile mediaFile) {
    String wopiServerFilesUrl;
    if (propertyHolder.isMsOfficeEnabled()) {
      wopiServerFilesUrl = msOfficeWopiServerUrlOverride;
    } else {
      wopiServerFilesUrl = propertyHolder.getServerUrl();
    }
    if (!wopiServerFilesUrl.startsWith("http")) {
      wopiServerFilesUrl = propertyHolder.getServerUrl();
    }

    wopiServerFilesUrl += "/wopi/files/" + mediaFile.getGlobalIdentifier();
    return wopiServerFilesUrl;
  }

  public String getUrlToOfficeOnlineViewPage(EcatMediaFile mediaFile, String mode) {
    return propertyHolder.getServerUrl()
        + "/officeOnline/"
        + mediaFile.getGlobalIdentifier()
        + "/"
        + mode;
  }
}
