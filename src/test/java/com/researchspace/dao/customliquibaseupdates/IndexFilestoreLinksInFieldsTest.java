package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.field.Field;
import com.researchspace.service.FieldManager;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.testutils.TestFactory;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class IndexFilestoreLinksInFieldsTest {

  @Mock private FieldManager fieldManager;
  @Mock private NfsFieldLinkManager nfsFieldLinkManager;
  @Mock private PlatformTransactionManager txManager;

  @Test
  void aFieldThatCannotBeIndexedIsSkippedAndTheOthersAreStillIndexed() {
    Field broken = TestFactory.createAnySDWithText("broken").getFields().get(0);
    Field fine = TestFactory.createAnySDWithText("fine").getFields().get(0);
    when(fieldManager.get(1L, null)).thenReturn(Optional.of(broken));
    when(fieldManager.get(2L, null)).thenReturn(Optional.of(fine));
    // a field's own SQL error, such as a trigger signalling 45000 at flush, is skipped
    doThrow(new IllegalStateException(new SQLException("rejected", "45000")))
        .when(nfsFieldLinkManager)
        .reindex(same(broken), eq("broken"));
    IndexFilestoreLinksInFields task = taskOverFields(1L, 2L);
    task.fieldTransaction = new TransactionTemplate(txManager);

    task.doExecute(null);

    verify(nfsFieldLinkManager).reindex(same(fine), eq("fine"));
    assertEquals("Filestore links indexed for 1/2 fields", task.getConfirmationMessage());
  }

  @Test
  void aFieldTransactionThatCannotStartFailsTheUpdateSoItRunsAgain() {
    when(txManager.getTransaction(any()))
        .thenThrow(new CannotCreateTransactionException("no connection"));
    IndexFilestoreLinksInFields task = taskOverFields(1L, 2L);
    task.fieldTransaction = new TransactionTemplate(txManager);

    assertThrows(CannotCreateTransactionException.class, () -> task.doExecute(null));

    verify(txManager).getTransaction(any());
    verifyNoInteractions(nfsFieldLinkManager);
  }

  @Test
  void aConnectionLostWhileIndexingFailsTheUpdateSoItRunsAgain() {
    Field field = TestFactory.createAnySDWithText("text").getFields().get(0);
    when(fieldManager.get(1L, null)).thenReturn(Optional.of(field));
    doThrow(new IllegalStateException(new SQLNonTransientConnectionException("gone")))
        .when(nfsFieldLinkManager)
        .reindex(same(field), eq("text"));
    IndexFilestoreLinksInFields task = taskOverFields(1L, 2L);
    task.fieldTransaction = new TransactionTemplate(txManager);

    assertThrows(IllegalStateException.class, () -> task.doExecute(null));

    verify(fieldManager, never()).get(2L, null);
  }

  private IndexFilestoreLinksInFields taskOverFields(Long... ids) {
    IndexFilestoreLinksInFields task =
        new IndexFilestoreLinksInFields() {
          @Override
          boolean anyFilestoreSaved() {
            return true;
          }

          @Override
          List<Long> candidateFieldIds() {
            return List.of(ids);
          }
        };
    task.fMger = fieldManager;
    task.nfsFieldLinkManager = nfsFieldLinkManager;
    return task;
  }

  @Test
  void withoutAnyFilestoreNoFieldIsScanned() {
    IndexFilestoreLinksInFields task =
        new IndexFilestoreLinksInFields() {
          @Override
          boolean anyFilestoreSaved() {
            return false;
          }

          @Override
          List<Long> candidateFieldIds() {
            throw new AssertionError("scanned the field table");
          }
        };
    task.fMger = fieldManager;
    task.nfsFieldLinkManager = nfsFieldLinkManager;

    task.doExecute(null);

    verifyNoInteractions(fieldManager, nfsFieldLinkManager);
  }
}
