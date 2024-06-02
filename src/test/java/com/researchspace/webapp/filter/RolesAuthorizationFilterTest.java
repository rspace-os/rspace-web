package com.researchspace.webapp.filter;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.service.impl.ShiroTestUtils;
import java.io.IOException;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class RolesAuthorizationFilterTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  static ShiroTestUtils shiroUtils;

  AnyOfRolesAuthorizationFilter filter;
  MockHttpServletRequest request;
  MockHttpServletResponse response;
  @Mock Subject subjct;

  @BeforeClass
  public static void beforeClass() {}

  @AfterClass
  public static void afterClass() {}

  @Before
  public void setUp() throws Exception {
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subjct);
    filter = new AnyOfRolesAuthorizationFilter();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @After
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
