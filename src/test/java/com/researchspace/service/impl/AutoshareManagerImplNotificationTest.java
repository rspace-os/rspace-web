package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AutoshareManagerImplNotificationTest {

  private AutoshareManagerImpl manager;

  @Before
  public void setUp() throws Exception {
    manager = new AutoshareManagerImpl();
    Field messagesField = AutoshareManagerImpl.class.getDeclaredField("messages");
    messagesField.setAccessible(true);
    messagesField.set(manager, new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  public void allSucceededEnabled() {
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        allSucceededResult();
    assertEquals(
        "Automatic sharing is now enabled. All your previously unshared work is now shared.",
        manager.createNotificationMessage(true, result));
  }

  @Test
  public void allSucceededDisabled() {
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        allSucceededResult();
    assertEquals(
        "Automatic sharing is now disabled. All your previously shared work is now unshared.",
        manager.createNotificationMessage(false, result));
  }

  @Test
  public void partialFailureEnabledIncludesFailureList() {
    RecordGroupSharing failure = mockFailure("doc1", "SD1");
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        new ServiceOperationResultCollection<>();
    result.addResult(null);
    result.addFailure(failure);

    String message = manager.createNotificationMessage(true, result);
    assertEquals(
        "Automatic sharing is now enabled. There was a problem, some of your previously unshared"
            + " work is still unshared - doc1 - SD1.",
        message);
  }

  @Test
  public void mayHaveFailedDisabledIncludesExceptionMessage() {
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        new ServiceOperationResultCollection<>();
    result.addException(new RuntimeException("boom"));

    String message = manager.createNotificationMessage(false, result);
    assertEquals(
        "Setting automatic sharing to disabled may have failed. Some of your previously shared"
            + " work is still shared - boom.",
        message);
  }

  private ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>
      allSucceededResult() {
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        new ServiceOperationResultCollection<>();
    result.addResult(null);
    return result;
  }

  private RecordGroupSharing mockFailure(String name, String globalId) {
    RecordGroupSharing failure = org.mockito.Mockito.mock(RecordGroupSharing.class);
    com.researchspace.model.record.BaseRecord shared =
        org.mockito.Mockito.mock(com.researchspace.model.record.BaseRecord.class);
    when(shared.getName()).thenReturn(name);
    when(shared.getGlobalIdentifier()).thenReturn(globalId);
    when(failure.getShared()).thenReturn(shared);
    return failure;
  }
}
