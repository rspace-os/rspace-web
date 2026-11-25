package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.DMPDao;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.dmp.DmpSourceDetector;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class DMPManagerImplTest {

  private @Mock DMPDao dmpDao;
  private @Mock DmpSourceDetector detector;
  private @Mock PlatformTransactionManager transactionManager;
  private DMPManagerImpl manager;
  private User user;

  @BeforeEach
  void setUp() {
    manager = new DMPManagerImpl(dmpDao);
    manager.setSourceDetector(detector);
    manager.setTransactionManager(transactionManager);
    user = TestFactory.createAnyUser("any");
  }

  @Test
  void planWithKnownSourceIsNotDetected() {
    givenPlans(plan(1L, DMPSource.DMP_ONLINE));

    assertEquals(DMPSource.DMP_ONLINE, manager.findDMPsForUser(user).get(0).getSource());
    verifyNoInteractions(detector, transactionManager);
  }

  @Test
  void unknownPlanCarriesDetectedSourceAndSavesItInItsOwnTransaction() {
    DMPUser plan = plan(1L, DMPSource.UNKNOWN);
    DMPUser storedRow = plan(1L, DMPSource.UNKNOWN);
    givenPlans(plan);
    when(detector.detect(plan)).thenReturn(DMPSource.ARGOS);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(dmpDao.get(1L)).thenReturn(storedRow);

    assertThat(manager.findDMPsForUser(user)).containsExactly(plan);

    assertEquals(DMPSource.ARGOS, plan.getSource());
    assertEquals(DMPSource.ARGOS, storedRow.getSource());
    verify(dmpDao).detach(plan);
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertEquals(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW,
        definition.getValue().getPropagationBehavior());
  }

  @Test
  void failedSaveDoesNotThrowAndPlanStillCarriesDetectedSource() {
    DMPUser plan = plan(1L, DMPSource.UNKNOWN);
    givenPlans(plan);
    when(detector.detect(plan)).thenReturn(DMPSource.DMP_TOOL);
    when(transactionManager.getTransaction(any()))
        .thenThrow(new IllegalStateException("database down"));

    assertDoesNotThrow(() -> manager.findDMPsForUser(user));
    assertEquals(DMPSource.DMP_TOOL, plan.getSource());
  }

  @Test
  void planStillUnknownAfterDetectionIsNotSaved() {
    DMPUser plan = plan(1L, DMPSource.UNKNOWN);
    givenPlans(plan);
    when(detector.detect(plan)).thenReturn(DMPSource.UNKNOWN);

    assertEquals(DMPSource.UNKNOWN, manager.findDMPsForUser(user).get(0).getSource());
    verifyNoInteractions(transactionManager);
    verify(dmpDao, never()).detach(any());
  }

  private void givenPlans(DMPUser... plans) {
    when(dmpDao.findDMPsForUser(user)).thenReturn(List.of(plans));
  }

  private DMPUser plan(long id, DMPSource source) {
    DMPUser plan = new DMPUser(user, new DmpDto("id" + id, "title" + id, source, null, null));
    ReflectionTestUtils.setField(plan, "id", id);
    return plan;
  }
}
