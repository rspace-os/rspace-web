package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.researchspace.Constants;
import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.model.User;
import com.researchspace.testutils.RunProfileTestConfiguration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration()
@RunProfileTestConfiguration
public class SysAdminConfigControllerMVCIT extends MVCTestBase {

  @Autowired private WhiteListedIPAddressManager ipMgr;

  @Before
  public void setup() throws Exception {
    super.setUp();
    removeExistingIPWhiteList();
  }

  private void removeExistingIPWhiteList() {
    for (WhiteListedSysAdminIPAddress add : ipMgr.getAll()) {
      ipMgr.remove(add.getId());
    }
  }

  @Test
  public void testLoadPage() throws Exception {
    mockMvc
        .perform(get("/system/config"))
        .andExpect(view().name(SysAdminConfigController.SYSTEM_CONFIG_PAGE));
  }

  @Test
  public void ipAddressCrudOperationsFailsIfNotSysadmin() throws Exception {
    User any = createAndSaveUser(getRandomAlphabeticString("user"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(any, pi, admin);
    User[] users = new User[] {any, pi, admin};
    for (User nonsysadmin : users) {
      logoutAndLoginAs(nonsysadmin);
      MvcResult result =
          mockMvc
              .perform(
                  post("/system/config/ajax/addIpAddress")
                      .param("ipAddress", "25")
                      .param("description", "any"))
              .andReturn();
      mvcUtils.assertAuthorizationException(result);
      result = getIpAddresses();
      mvcUtils.assertAuthorizationException(result);
      result = mockMvc.perform(post("/system/config/ajax/removeIpAddress/{id}", 1L)).andReturn();
      mvcUtils.assertAuthorizationException(result);
    }
  }

  @Test
  public void ipAddressCrudOperations() throws Exception {

    User sysadmin = logoutAndLoginAsSysAdmin();

    MvcResult result;
    assertIpsCount(0);
    // empty text rejected
    result =
        mockMvc
            .perform(
                post("/system/config/ajax/addIpAddress")
                    .param("ipAddress", "")
                    .param("description", "any"))
            .andReturn();
    Map data = mvcUtils.parseJSONObjectFromResponseStream(result);
    assertNotNull(data.get("errorMsg"));

    // now add 1
    result =
        mockMvc
            .perform(
                post("/system/config/ajax/addIpAddress")
                    .param("ipAddress", "25")
                    .param("description", "any"))
            .andReturn();

    WhiteListedSysAdminIPAddress added =
        mvcUtils.getFromJsonAjaxReturnObject(result, WhiteListedSysAdminIPAddress.class);
    assertIpsCount(1);
    assertEquals(new WhiteListedSysAdminIPAddress("25"), added);
    assertNotNull(added.getId());

    // now update it (rspac-722)
    result =
        mockMvc
            .perform(
                post("/system/config/ajax/updateIpAddress")
                    .param("ipAddress", "26")
                    .param("description", "any2")
                    .param("id", added.getId() + ""))
            .andReturn();
    WhiteListedSysAdminIPAddress updated =
        mvcUtils.getFromJsonAjaxReturnObject(result, WhiteListedSysAdminIPAddress.class);
    assertIpsCount(1);
    assertEquals(new WhiteListedSysAdminIPAddress("26"), updated);

    // now delete it, should be removed
    result =
        mockMvc
            .perform(post("/system/config/ajax/removeIpAddress/{id}", added.getId()))
            .andReturn();
    assertIpsCount(0);
  }

  private List<WhiteListedSysAdminIPAddress> assertIpsCount(int expected)
      throws Exception, JsonParseException, JsonMappingException, IOException {
    MvcResult result = getIpAddresses();
    List<WhiteListedSysAdminIPAddress> ips = getIpsFromResponse(result);
    assertEquals(expected, ips.size());
    return ips;
  }

  private MvcResult getIpAddresses() throws Exception {
    return mockMvc.perform(get("/system/config/ajax/ipAddresses")).andReturn();
  }

  private List<WhiteListedSysAdminIPAddress> getIpsFromResponse(MvcResult result)
      throws JsonMappingException, IOException {
    Map aro = mvcUtils.parseJSONObjectFromResponseStream(result);
    return (List) aro.get("data");
  }
}
