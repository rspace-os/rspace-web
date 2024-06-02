package com.researchspace.service.impl;

import com.researchspace.core.util.version.Versionable;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.RemoteLicenseService;
import com.researchspace.service.UserStatisticsManager;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/** Gets diagnostic information on the server and sends it to the license server */
@Service
public class LicenseServerChecker extends AbstractAppInitializor {

  private RemoteLicenseService licenseService;

  /**
   * @param licenseService the licenseService to set
   */
  @Autowired
  public void setLicenseService(RemoteLicenseService licenseService) {
    this.licenseService = licenseService;
  }

  @Autowired private UserStatisticsManager statsMgr;

  @Autowired private List<Versionable> versionables;

  @Override
  public void onAppStartup(ApplicationContext context) {
    log.info("Contacting license server ...");
    if (!licenseService.isAvailable()) {
      fatalStartUpLog("License server is unavailable!");
      return;
    }

    String data = getServerData();
    String macId = getMacId();
    UserStatistics userinfo = getUserInfoData();
    if (StringUtils.isEmpty(data) || StringUtils.isEmpty(macId)) {
      log.warn("Skipping license server check; macid or server data is unobtainable");
      return;
    }
    boolean updated = licenseService.uploadServerData(data, macId, userinfo.getTotalEnabledUsers());
    if (!updated) {
      fatalStartUpLog(
          "Could not upload server info to license server - has license server been updated to"
              + " 0.2.2?");
    } else {
      log.info("License server contacted OK!");
    }
  }

  private UserStatistics getUserInfoData() {
    UserStatistics stats = statsMgr.getUserStats(7);
    log.info("Calculating usage statistics: " + stats.toString());
    return stats;
  }

  private String getMacId() {
    try {

      InetAddress ip = InetAddress.getLocalHost();
      log.info("Current IP address : " + ip.getHostAddress());

      NetworkInterface network = NetworkInterface.getByInetAddress(ip);
      if (network == null) {
        log.warn("Could not acquire the NetworkInterface for IP: " + ip);
        return "";
      }

      byte[] mac = network.getHardwareAddress();
      if (mac == null) {
        log.warn("Could not acquire the MAC address");
        return "";
      }

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < mac.length; i++) {
        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
      }
      log.info("Current MAC address : " + sb);
      return sb.toString();

    } catch (UnknownHostException | SocketException e) {
      log.error("Could not determine the the MAC address.", e);
      return "";
    }
  }

  private String getServerData() {
    return "Environment Data Info:\n"
        + getEnvironmentVarsAsString()
        + "\n System properties "
        + getSystemPropertiesAsString()
        + "\nVersion info\n"
        + getVersionInfo();
  }

  private String getVersionInfo() {
    List<String> versionInfo = new ArrayList<>();
    log.info("Getting version info from " + versionables.size() + " version sources");
    for (Versionable versionable : versionables) {
      versionInfo.add(versionable.getDescription() + ": version " + versionable.getVersion());
    }
    String versionStr = StringUtils.join(versionInfo, ",");
    log.info("[Version info: " + versionStr + "]");
    return versionStr;
  }
}
