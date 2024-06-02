package com.researchspace.auth;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.core.util.SubnetUtils;
import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.util.List;
import javax.servlet.ServletRequest;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class WhiteListIPCheckerImpl implements WhiteListIPChecker {

  private @Autowired IPropertyHolder properties;
  private @Autowired WhiteListedIPAddressManager ipMgr;

  /**
   * Validates the ip address of an incoming servelt request against a whitelist of
   * WhiteListedSysAdminIPAddress
   *
   * @param request
   * @param allowedIps
   * @param securityLog
   * @return <code>true</code> if whitelist is enabled, <code>false</code> otherwise
   */
  public boolean isRequestWhitelisted(ServletRequest request, User subject, Logger securityLog) {
    boolean ipFound = true;
    if (subject.hasRole(Role.SYSTEM_ROLE) && properties.isSysadminWhiteListingEnabled()) {
      ipFound = false;
      List<WhiteListedSysAdminIPAddress> allowedIps = ipMgr.getAll();
      if (allowedIps.isEmpty()) {
        securityLog.error(
            "Ip address list is empty); sysadmin cannot login! Set deployment property "
                + "'sysadmin.limitedIpAddresses.enabled' to false to allow login.");
      }

      String requestIp = RequestUtil.remoteAddr(WebUtils.toHttp(request));
      for (WhiteListedSysAdminIPAddress address : allowedIps) {
        if (address.getIpAddress().equalsIgnoreCase(requestIp)) {
          ipFound = true;
          break;
        }
        if (SubnetUtils.isValidIp4OrCIDRAddress(requestIp)) {
          if (SubnetUtils.isValidCIDRAddress(address.getIpAddress())) {
            try {
              SubnetUtils subnetUtils = new SubnetUtils(address.getIpAddress());
              subnetUtils.setInclusiveHostCount(true);
              if (subnetUtils.getInfo().isInRange(requestIp)) {
                ipFound = true;
                break;
              }
            } catch (
                IllegalArgumentException iae) { // iae thrown from library code if mask not valid
              securityLog.warn(
                  "Possibly invalid ip whitelist setup for whitelisted address {} and request IP"
                      + " {}",
                  address.getIpAddress(),
                  requestIp);
            }
          }
        }
      }
    }
    return ipFound;
  }
}
