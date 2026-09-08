package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.BaseManagerTestCaseBase.MockPrincipal;
import java.security.Principal;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicStructuredDocumentCommentsControllerTest {

  @Mock private StructuredDocumentController structuredDocumentController;
  @InjectMocks private PublicStructuredDocumentCommentsController controller;

  @BeforeEach
  void setUp() {
    controller.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void getCommentsRejectsMissingPrincipal() {
    // RSDEV-1329: fail closed when the anonymous login has not happened on the public route
    AuthorizationException e =
        assertThrows(AuthorizationException.class, () -> controller.getComments(2L, null, null));
    // The message can reach the viewer via the ajax error view, so it must resolve from the
    // catalog rather than ship as a hard-coded literal
    assertTrue(
        e.getMessage().contains(RecordGroupSharing.ANONYMOUS_USER),
        "refusal should resolve the externalized message, got: " + e.getMessage());
    verifyNoInteractions(structuredDocumentController);
  }

  @Test
  void getCommentsPassesCommentIdRevisionAndPrincipalToTheDelegateUnchanged() {
    // The public route exists only to reach the editor controller's comment reader, which is
    // where the READ assertion lives. Pinning the arguments keeps a future edit from dropping
    // the revision (audited comments on a published revision) or substituting a different
    // subject, either of which would silently change what anonymous callers can read
    // (RSDEV-1329).
    Principal principal = new MockPrincipal(RecordGroupSharing.ANONYMOUS_USER);
    List<EcatCommentItem> expected = List.of(new EcatCommentItem());
    when(structuredDocumentController.getComments(2L, 3, principal)).thenReturn(expected);

    assertEquals(expected, controller.getComments(2L, 3, principal));

    verify(structuredDocumentController).getComments(2L, 3, principal);
  }

  @Test
  void getCommentsPassesANullRevisionThroughForTheCurrentVersion() {
    // revision is optional on the endpoint; absent means "current", and must not be defaulted
    Principal principal = new MockPrincipal(RecordGroupSharing.ANONYMOUS_USER);
    when(structuredDocumentController.getComments(2L, null, principal)).thenReturn(List.of());

    controller.getComments(2L, null, principal);

    verify(structuredDocumentController).getComments(2L, null, principal);
  }
}
