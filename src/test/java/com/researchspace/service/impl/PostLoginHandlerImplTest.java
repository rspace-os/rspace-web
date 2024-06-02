package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.PostAnyLoginAction;
import com.researchspace.service.PostFirstLoginAction;
import com.researchspace.session.SessionAttributeUtils;
import java.util.List;
import javax.servlet.http.HttpSession;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpSession;

public class PostLoginHandlerImplTest {

  private static final String AFTER_INIT_REDIRECT = "afterInit";
  private static final String BEFORE_INIT_REDIRECT = "/beforeInit";
  private static final String ANY_LOGIN_REDIRECT = "xyz";
  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  @Mock IContentInitializer contentInit;
  @Mock UserContentUpdater ucMock;
  @InjectMocks PostLoginHandlerImpl postLoginHandler;
  MockHttpSession mockSession;
  private User anyUser;

  @Before
  public void setup() {
    mockSession = new MockHttpSession();
    anyUser = TestFactory.createAnyUser("any");
    anyUser.setId(1L);
  }

  @Test
  public void firstLoginCallsContentInitialisor() {
    setupAsFirstLogin();
    postLoginHandler.handlePostLogin(anyUser, mockSession);
    assertContentInit();
  }

  @Test
  public void firstLoginNotCallsContentInitialisorIfAlreadyHandled() {
    setupAsFirstLoginHandled();
    postLoginHandler.handlePostLogin(anyUser, mockSession);
    assertNoContentInitIsInvoked();
  }

  // RSPAC-1980
  @Test
  public void firstLoginInvokedIfContentNotInitialised() {
    // this is default setting, just stating explicitly here
    anyUser.setContentInitialized(false);
    // it's not first session, but content not initialised, so we are calling it here.
    postLoginHandler.handlePostLogin(anyUser, mockSession);
    assertContentInit();
  }

  @Test
  public void contentInitialisorNotCalledIfNotFirstLogin() {
    anyUser.setContentInitialized(true);
    postLoginHandler.handlePostLogin(anyUser, mockSession);
    assertNoContentInitIsInvoked();
  }

  private void assertNoContentInitIsInvoked() {
    verify(contentInit, never()).init(anyUser.getId());
  }

  @Test
  public void onlyAnyLoginRedirect() {
    postLoginHandler.setPostAnyLoginActions(createAnyLoginActions(ANY_LOGIN_REDIRECT));
    anyUser.setContentInitialized(true);
    assertEquals(ANY_LOGIN_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertNoContentInitIsInvoked();
  }

  @Test
  public void everyLoginCallsForUserContentUpdate() {
    postLoginHandler.setPostAnyLoginActions(createAnyLoginActions(ANY_LOGIN_REDIRECT));
    anyUser.setContentInitialized(true);
    postLoginHandler.handlePostLogin(anyUser, mockSession);
    verify(ucMock).doUserContentUpdates(eq(anyUser));
  }

  @Test
  public void onlyFirstLoginRedirectReturnsBeforeContentInit() {
    setupAsFirstLogin();
    postLoginHandler.setPostFirstLoginActions(
        createFirstLoginActions(BEFORE_INIT_REDIRECT, AFTER_INIT_REDIRECT));
    assertEquals(BEFORE_INIT_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertNoContentInitIsInvoked();
    assertEquals(AFTER_INIT_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertContentInit();
  }

  @Test
  public void onlyFirstLoginRedirectReturnsAfterContentInit() {
    setupAsFirstLogin();
    postLoginHandler.setPostFirstLoginActions(createFirstLoginActions(null, AFTER_INIT_REDIRECT));
    assertEquals(AFTER_INIT_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertContentInit();
  }

  @Test
  public void onlyFirstLoginRedirectNullIfActionsReturnNull() {
    setupAsFirstLogin();
    postLoginHandler.setPostFirstLoginActions(createFirstLoginActions(null, null));
    postLoginHandler.setPostAnyLoginActions(createAnyLoginActions(null));
    assertEquals(null, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertContentInit();
  }

  @Test
  public void firstLoginHandledRequiresAllHandlersToBeSetComplete() {
    setupAsFirstLogin();
    TestAfterHelper afterHelper = createAfterHelper(AFTER_INIT_REDIRECT);
    TestBeforeHelper beforeHelper = createBeforeHelper(BEFORE_INIT_REDIRECT);
    postLoginHandler.setPostFirstLoginActions(toList(beforeHelper, afterHelper));
    // first handler redirects, second handler not called yet
    // initially no handlers have been invoked
    assertEquals(0, beforeHelper.invocationCountSpy);
    assertEquals(0, afterHelper.invocationCountSpy);
    assertEquals(BEFORE_INIT_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertNull(mockSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN_HANDLED));
    assertEquals(1, beforeHelper.invocationCountSpy);
    assertEquals(0, afterHelper.invocationCountSpy);

    // second invocation calls second handler with redirect
    assertEquals(AFTER_INIT_REDIRECT, postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertEquals(1, beforeHelper.invocationCountSpy);
    assertEquals(1, afterHelper.invocationCountSpy);

    // still not marked complete, maybe there are more handlers to invoke?
    assertNull(mockSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN_HANDLED));

    // now all handlers have been invoked. They are each called only once, i.e they are not invoked
    // again
    assertNull(postLoginHandler.handlePostLogin(anyUser, mockSession));
    assertEquals(1, beforeHelper.invocationCountSpy);
    assertEquals(1, afterHelper.invocationCountSpy);
    assertTrue((Boolean) mockSession.getAttribute(SessionAttributeUtils.FIRST_LOGIN_HANDLED));
  }

  private void assertContentInit() {
    verify(contentInit).init(anyUser.getId());
  }

  private void setupAsFirstLogin() {
    mockSession.setAttribute(SessionAttributeUtils.FIRST_LOGIN, Boolean.TRUE);
  }

  private void setupAsFirstLoginHandled() {
    setupAsFirstLogin();
    mockSession.setAttribute(SessionAttributeUtils.FIRST_LOGIN_HANDLED, Boolean.TRUE);
    anyUser.setContentInitialized(true);
  }

  List<PostAnyLoginAction> createAnyLoginActions(String redirectUrl) {
    PostAnyLoginAction action = (anyUser, mockSession) -> redirectUrl;
    return toList(action);
  }

  List<PostFirstLoginAction> createFirstLoginActions(
      String beforeInitRedirectReturnValue, String afterInitRedirectReturnValue) {
    return toList(
        createBeforeHelper(beforeInitRedirectReturnValue),
        createAfterHelper(afterInitRedirectReturnValue));
  }

  private TestAfterHelper createAfterHelper(String afterInitRedirectReturnValue) {
    return new TestAfterHelper(afterInitRedirectReturnValue);
  }

  private TestBeforeHelper createBeforeHelper(String beforeInitRedirectReturnValue) {
    return new TestBeforeHelper(beforeInitRedirectReturnValue);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  static class TestBeforeHelper extends AbstractPostFirstLoginHelper {

    TestBeforeHelper(String beforeInitRedirectReturnValue) {
      this.beforeInitRedirectReturnValue = beforeInitRedirectReturnValue;
    }

    private String beforeInitRedirectReturnValue;
    int invocationCountSpy = 0;

    @Override
    protected String getSessionAttributeName() {
      return "TEST_BEFOREATTRIBUTE";
    }

    public String doFirstLoginBeforeContentInitialisation(User user, HttpSession session) {
      setCompleted(user, session, getSessionAttributeName());
      invocationCountSpy++;
      return beforeInitRedirectReturnValue;
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  static class TestAfterHelper extends AbstractPostFirstLoginHelper {

    TestAfterHelper(String afterInitRedirectReturnValue) {
      this.afterInitRedirectReturnValue = afterInitRedirectReturnValue;
    }

    private String afterInitRedirectReturnValue;
    int invocationCountSpy = 0;

    @Override
    protected String getSessionAttributeName() {
      return "TEST_AFTERATTRIBUTE";
    }

    public String doFirstLoginAfterContentInitialisation(
        User user, HttpSession session, InitializedContent inititalizedContent) {
      setCompleted(user, session, getSessionAttributeName());
      invocationCountSpy++;
      return afterInitRedirectReturnValue;
    }
  }
}
