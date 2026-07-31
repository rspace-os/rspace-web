package com.axiope.userimport;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.LoginHelper;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserRoleHandler;
import com.researchspace.testutils.TestFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class DefaultPostUserCreateTest {

  @Mock IPropertyHolder properties;
  @Mock LoginHelper loginHelper;
  @Mock UserRoleHandler roleHandler;
  @Mock AuditTrailService auditService;
  @InjectMocks DefaultPostUserCreate postUserCreate;
  HttpServletRequest mockRequest;
  User anyUser;

  @BeforeEach
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
