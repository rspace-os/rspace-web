package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyMessageForRecipientOfType;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static com.researchspace.model.record.TestFactory.createAnyUserWithRole;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PostPiChangeHandlerTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock CommunicationManager commMgr;
  @Mock CommunicationDao commDao;
  @Mock SignatureDao sigDao;
  @Mock ISearchResults<MessageOrRequest> results;
  @Mock IGroupPermissionUtils permUtils;

  @InjectMocks PiChangeHandlerImpl handler;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

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
