package com.researchspace.netfiles.samba;

import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
class SambaUtils {

  public static URL parseSambaUrl(String serverUrl) throws MalformedURLException {
    return new URL(null, serverUrl, new SambaUrlStreamHandler());
  }

  public static String getSmbFilePathForTarget(String serverUrl, String target) {
    String connectionPath = serverUrl;
    String filePath = null;
    if (target != null) {
      filePath = target.trim();
    }
    if (StringUtils.isNotEmpty(filePath)) {
      if (!filePath.startsWith("/") && !connectionPath.endsWith("/")) {
        connectionPath += "/";
      }
      connectionPath += filePath;
    }
    if (!connectionPath.endsWith("/")) {
      connectionPath += "/";
    }
    return connectionPath;
  }

  public String getNameInformationFromSmbjFileInfo(
      FileAllInformation smbjFileInfo, String path, boolean sambaNameMustMatchPath) {
    String nameInfo = smbjFileInfo.getNameInformation();
    String targetPathParsed = path.replace("/", "\\");
    // name info can be empty for some samba implementations, see SUPPORT-284
    if (StringUtils.isEmpty(nameInfo)) {
      nameInfo = targetPathParsed;
      log.info("recalculated nameInfo: " + nameInfo);
    } // see https://researchspace.atlassian.net/browse/RSPAC-2440
    if (sambaNameMustMatchPath
        && (!nameInfo.replaceAll("\\\\", "").equals(targetPathParsed.replaceAll("\\\\", "")))) {
      log.warn(
          String.format(
              "name value from samba: %s , did not match target directory: %s ",
              nameInfo, targetPathParsed));
      return targetPathParsed;
    }
    return nameInfo;
  }
}
