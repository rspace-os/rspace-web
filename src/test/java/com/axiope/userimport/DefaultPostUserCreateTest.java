package com.axiope.userimport;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.LoginHelper;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserRoleHandler;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class DefaultPostUserCreateTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock IPropertyHolder properties;
  @Mock LoginHelper loginHelper;
  @Mock UserRoleHandler roleHandler;
  @Mock AuditTrailService auditService;
  @InjectMocks DefaultPostUserCreate postUserCreate;
  HttpServletRequest mockRequest;
  User anyUser;

  @Before
  public void setup() throws Exception {
    mockRequest = new MockHttpServletRequest();
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  public void postUserCreateAssertions() {
    postUserCreate.postUserCreate(anyUser, mockRequest, "any");
    assertLoginAndNotifyCalled();
    assertGroupNotCreated();
  }

  @Test
  public void postUserCreateMakesGroupIfEnabled() {
    //
    when(properties.isPicreateGroupOnSignupEnabled()).thenReturn(Boolean.TRUE);
    postUserCreate.postUserCreate(anyUser, mockRequest, "any");
    assertLoginAndNotifyCalled();
    assertGroupNotCreated();

    anyUser.setPicreateGroupOnSignup(true);
    when(properties.isPicreateGroupOnSignupEnabled()).thenReturn(Boolean.FALSE);
    assertLoginAndNotifyCalled();
    assertGroupNotCreated();

    // both conditions must be true
    when(properties.isPicreateGroupOnSignupEnabled()).thenReturn(Boolean.TRUE);
    when(roleHandler.setNewlySignedUpUserAsPi(anyUser)).thenReturn(anyUser);
    postUserCreate.postUserCreate(anyUser, mockRequest, "any");
    assertLoginAndNotifyCalled();
    assertGroupCreated();
  }

  private void assertGroupCreated() {
    verify(roleHandler).setNewlySignedUpUserAsPi(anyUser);
  }

  private void assertGroupNotCreated() {
    verify(roleHandler, never()).setNewlySignedUpUserAsPi(anyUser);
  }

  private void assertLoginAndNotifyCalled() {
    verify(loginHelper, atLeastOnce()).login(anyUser, "any", mockRequest);
    verify(auditService, atLeastOnce()).notify(Mockito.any(HistoricalEvent.class));
  }
}
