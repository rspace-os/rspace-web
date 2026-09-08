package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
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
}
