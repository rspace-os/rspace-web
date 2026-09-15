package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.BaseManagerTestCaseBase.MockPrincipal;
import com.researchspace.testutils.TestFactory;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fast unit coverage for the subject resolution on {@code /gallery/ajax/getLinkedDocuments}. The
 * authorization behaviour itself lives in {@link
 * com.researchspace.service.impl.MediaManagerImpl#getIdsOfLinkedDocuments} and is covered by
 * MediaManagerImplTest; what matters here is only how the controller obtains the subject
 * (RSDEV-1329).
 */
@ExtendWith(MockitoExtension.class)
class GalleryControllerLinkedDocumentsTest {

  @Mock private MediaManager mediaManager;
  @Mock private UserManager userManager;

  private GalleryController galleryController;
  private User user;

  @BeforeEach
  void setUp() {
    galleryController = new GalleryController();
    ReflectionTestUtils.setField(galleryController, "mediaManager", mediaManager);
    galleryController.setUserManager(userManager);
    user = TestFactory.createAnyUser("any");
  }

  @Test
  void resolvesTheSubjectFromThePrincipalWithoutCreatingASession() {
    // RSDEV-1329: this endpoint is also mapped under the anon /public/** prefix, where
    // UserManagerImpl.getAuthenticatedUserInSession would CREATE a session per request
    // (SecurityUtils.getSubject().getSession()), letting an unauthenticated caller force
    // unbounded session allocation before the refusal fires. The principal carries the subject
    // already, so no session is needed.
    Principal principal = new MockPrincipal(user.getUsername());
    when(userManager.getUserByUsername(user.getUsername())).thenReturn(user);
    RecordInformation info = new RecordInformation();
    when(mediaManager.getIdsOfLinkedDocuments(1L, user)).thenReturn(List.of(info));

    AjaxReturnObject<List<RecordInformation>> result =
        galleryController.getDocumentsLinkedToAttachment(1L, principal);

    assertEquals(List.of(info), result.getData());
    verify(userManager, never()).getAuthenticatedUserInSession();
  }

  @Test
  void passesANullSubjectForASessionlessRequestSoTheManagerRefuses() {
    // fail closed without a session lookup: the manager is the single place that decides
    galleryController.getDocumentsLinkedToAttachment(1L, null);

    verify(mediaManager).getIdsOfLinkedDocuments(1L, null);
    verify(userManager, never()).getAuthenticatedUserInSession();
  }
}
