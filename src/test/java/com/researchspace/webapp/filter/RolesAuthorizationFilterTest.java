package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.service.impl.ShiroTestUtils;
import java.io.IOException;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class RolesAuthorizationFilterTest {
  static ShiroTestUtils shiroUtils;

  AnyOfRolesAuthorizationFilter filter;
  MockHttpServletRequest request;
  MockHttpServletResponse response;
  @Mock Subject subjct;

  @BeforeEach
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subjct);
    filter = new AnyOfRolesAuthorizationFilter();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @AfterEach
  public void tearDown() throws Exception {
    shiroUtils.clearSubject();
  }

  @Test
  public void testIsAccessAllowed() throws IOException {
    when(subjct.hasRole(Constants.ADMIN_ROLE)).thenReturn(true);

    assertTrue(
        filter.isAccessAllowed(
            request, response, new String[] {Constants.ADMIN_ROLE, Constants.SYSADMIN_ROLE}));

    when(subjct.hasRole(Constants.SYSADMIN_ROLE)).thenReturn(true);

    assertTrue(filter.isAccessAllowed(request, response, new String[] {Constants.SYSADMIN_ROLE}));
  }
}
