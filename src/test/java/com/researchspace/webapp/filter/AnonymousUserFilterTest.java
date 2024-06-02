package com.researchspace.webapp.filter;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

public class AnonymousUserFilterTest {

  private static ThreadState subjectThreadState;
  private AnonymousUserFilter testee;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;
  @Mock private FilterConfig filterConfig;
  @Mock private Subject subject;
  @Mock private Session session;
  @Mock private User anonymousUser;
  @Mock private User nonAnonymousUser;
  @Captor private ArgumentCaptor<String> redirectUrlCaptor;

  @Before
  public void setUp() {

    openMocks(this);
    subjectThreadState = new SubjectThreadState(subject);
    subjectThreadState.bind();
    testee = new AnonymousUserFilter();
    ReflectionTestUtils.setField(testee, "filterConfig", filterConfig);
    when(filterConfig.getInitParameter("urlsAllowedForAnonymousAccess")).thenReturn("/public,/api");
    when(request.getRequestURI()).thenReturn("/will_be_filtered");
    when(subject.getSession()).thenReturn(session);
    when(anonymousUser.getUsername()).thenReturn(RecordGroupSharing.ANONYMOUS_USER);
    when(anonymousUser.isAnonymousGuestAccount()).thenReturn(true);
    when(nonAnonymousUser.getUsername()).thenReturn("auser");
  }

  @After
  public void tearDown() {
    subjectThreadState.clear();
  }

  private void setUpAUrlWhichWillNotBeFiltered() {
    when(request.getRequestURI()).thenReturn("/public");
  }

  private void setUpUserInSession(String userName) {
    if (userName.equals("anon")) {
      when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(anonymousUser);
    } else if (userName.equals("not anonymous")) {
      when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(nonAnonymousUser);
    } else if (userName.equals("none")) {
      when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(null);
    }
  }

  // The anonymous user is not allowed to use ANY request url except one on a whitelist (defined in
  // web.xml).
  @Test
  public void testBlocksAnonymousUserWhenRequestUrlNotExcludedFromFiltering()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    testee.doFilterInternal(request, response, chain);
    verify(chain, never()).doFilter(eq(request), eq(response));
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestHasWorkspace()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/workspace");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestHasDasboard()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/dashboard");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestHasApps()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/apps");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestHasSystem()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/system");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestHasGroups()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/groups");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testRedirectsAnonymousUserToLoginWhenRequestIsToRoot()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/");
    doFilterAndMakeRedirectAssertions();
  }

  @Test
  public void testDoesNotRedirectsAnonymousUserToLoginWhenRequestIsToGallery()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/gallery");
    doFilterAndMakeNoRedirectAssertions();
  }

  @Test
  public void testDoesNotRedirectsAnonymousUserToLoginWhenRequestIsToUserForm()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/userform");
    doFilterAndMakeNoRedirectAssertions();
  }

  @Test
  public void testDoesNotRedirectsAnonymousUserToLoginWhenRequestIsToPoll()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/poll");
    doFilterAndMakeNoRedirectAssertions();
  }

  @Test
  public void testDoesNotRedirectsAnonymousUserToLoginWhenRequestIsToMaintenance()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    when(request.getRequestURI()).thenReturn("/ajax/nextMaintenance");
    doFilterAndMakeNoRedirectAssertions();
  }

  private void doFilterAndMakeRedirectAssertions() throws ServletException, IOException {
    testee.doFilterInternal(request, response, chain);
    verify(chain, never()).doFilter(eq(request), eq(response));
    verify(response).encodeRedirectURL(redirectUrlCaptor.capture());
    assertTrue(redirectUrlCaptor.getValue().contains("/login"));
  }

  private void doFilterAndMakeNoRedirectAssertions() throws ServletException, IOException {
    testee.doFilterInternal(request, response, chain);
    verify(chain, never()).doFilter(eq(request), eq(response));
    verify(response, never()).encodeRedirectURL(redirectUrlCaptor.capture());
  }

  @Test
  public void testAllowProgressAnonymousUserWhenRequestUrlExcludedFromFiltering()
      throws ServletException, IOException {
    setUpUserInSession("anon");
    setUpAUrlWhichWillNotBeFiltered();
    testee.doFilterInternal(request, response, chain);
    verify(chain, times(1)).doFilter(eq(request), eq(response));
  }

  @Test
  public void testAllowProgressNonAnonymousUserAndWhenRequestUrlNotExcludedFromFiltering()
      throws ServletException, IOException {
    setUpUserInSession("not anonymous");
    testee.doFilterInternal(request, response, chain);
    verify(chain, times(1)).doFilter(eq(request), eq(response));
  }

  @Test
  public void testAllowProgressWhenNoUserAndWhenRequestUrlNotExcludedFromFiltering()
      throws ServletException, IOException {
    setUpUserInSession("none");
    testee.doFilterInternal(request, response, chain);
    verify(chain, times(1)).doFilter(eq(request), eq(response));
  }

  @Test
  public void testRequestToUrlExcludedFromFilteringDoesNotAttemptToCreateSession()
      throws ServletException, IOException {
    setUpAUrlWhichWillNotBeFiltered();
    testee.doFilterInternal(request, response, chain);
    verify(subject, never()).getSession();
  }
}
