package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.TestFactory.createAnyGroup;
import static com.researchspace.testutils.TestFactory.createAnyMessageForRecipientOfType;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static com.researchspace.testutils.TestFactory.createAnyUserWithRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.SignatureDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.service.impl.PiChangeHandlerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PostPiChangeHandlerTest {
  @Mock CommunicationManager commMgr;
  @Mock CommunicationDao commDao;
  @Mock SignatureDao sigDao;
  @Mock ISearchResults<MessageOrRequest> results;
  @Mock IGroupPermissionUtils permUtils;

  @InjectMocks PiChangeHandlerImpl handler;

  @Test
  public void witnessesTransferred() {
    User oldPi = createAnyUserWithRole("oldpi", Role.PI_ROLE.getName());
    User newPi = createAnyUserWithRole("newpi", Role.PI_ROLE.getName());
    User witnessRequestor = createAnyUser("witnessRequestor");
    Group group = createAnyGroup(newPi, oldPi, witnessRequestor);
    Witness witness = new Witness(oldPi);
    MessageOrRequest mor =
        createAnyMessageForRecipientOfType(
            witnessRequestor, oldPi, MessageType.REQUEST_RECORD_WITNESS);
    Mockito.when(results.getResults()).thenReturn(toList(mor));
    Mockito.when(sigDao.getOpenWitnessesByWitnessUser(oldPi)).thenReturn(toList(witness));
    Mockito.when(
            commMgr.getActiveMessagesAndRequestsForUserTargetByType(
                Mockito.eq(oldPi.getUsername()),
                Mockito.any(PaginationCriteria.class),
                Mockito.any(MessageTypeFilter.class)))
        .thenReturn(results);

    handler.afterPiChanged(oldPi, group, newPi, anyContext());
    assertOldPiNoLongerHasWitnessRequest(oldPi, mor);
    assertNewPiHasWitnessRequest(newPi, mor);
    assertEquals(newPi, witness.getWitness());
  }

  private PiChangeContext anyContext() {
    return new PiChangeContext(true);
  }

  private void assertNewPiHasWitnessRequest(User newPi, MessageOrRequest mor) {
    assertTrue(
        mor.getRecipients().stream()
            .anyMatch(ct -> ct.getRecipient().getUsername().equals(newPi.getUsername())));
  }

  private void assertOldPiNoLongerHasWitnessRequest(User oldPi, MessageOrRequest mor) {
    assertFalse(
        mor.getRecipients().stream()
            .anyMatch(ct -> ct.getRecipient().getUsername().equals(oldPi.getUsername())));
  }
}
