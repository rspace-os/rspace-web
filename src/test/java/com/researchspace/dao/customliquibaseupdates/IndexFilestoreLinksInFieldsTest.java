package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.model.field.Field;
import com.researchspace.service.FieldManager;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    when(fieldManager.findByTextContent(FieldParserConstants.NET_FS_CLASSNAME))
        .thenReturn(List.of(broken, fine));
    doThrow(new IllegalStateException("cannot index"))
        .when(nfsFieldLinkManager)
        .reindex(same(broken), eq("broken"));
    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    task.fMger = fieldManager;
    task.nfsFieldLinkManager = nfsFieldLinkManager;
    task.fieldTransaction = new TransactionTemplate(txManager);

    task.doExecute(null);

    verify(nfsFieldLinkManager).reindex(same(fine), eq("fine"));
    assertEquals("Filestore links indexed for 1/2 fields", task.getConfirmationMessage());
  }

  @Test
  void anAutosaveCopyIsNotIndexed() {
    Field saved = TestFactory.createAnySDWithText("saved").getFields().get(0);
    Field autosaveCopy = (Field) saved.shallowCopy();
    when(fieldManager.findByTextContent(FieldParserConstants.NET_FS_CLASSNAME))
        .thenReturn(List.of(saved, autosaveCopy));
    IndexFilestoreLinksInFields task = new IndexFilestoreLinksInFields();
    task.fMger = fieldManager;
    task.nfsFieldLinkManager = nfsFieldLinkManager;
    task.fieldTransaction = new TransactionTemplate(txManager);

    task.doExecute(null);

    verify(nfsFieldLinkManager).reindex(same(saved), eq("saved"));
    verifyNoMoreInteractions(nfsFieldLinkManager);
    assertEquals("Filestore links indexed for 1/1 fields", task.getConfirmationMessage());
  }
}
