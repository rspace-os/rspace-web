package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.MultipleSharesPermittedDocSharedStatusCalculatorImpl;
import com.researchspace.testutils.TestFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MultipleSharesPermittedDocSharedStatusCalculatorImplTest {
  @Mock private RecordGroupSharingDao groupSharingDao;
  MultipleSharesPermittedDocSharedStatusCalculatorImpl sharingStatusImpl;

  @BeforeEach
  public void setUp() {
    sharingStatusImpl = new MultipleSharesPermittedDocSharedStatusCalculatorImpl();
    sharingStatusImpl.setGroupshareRecordDao(groupSharingDao);
  }

  @Test
  public void testCalculateIfAlreadyShared() {
    StructuredDocument doc = TestFactory.createAnySD();
    doc.setId(1L);
    User toShareWith = TestFactory.createAnyUser("any");
    toShareWith.addRole(Role.PI_ROLE);
    AbstractUserOrGroupImpl toShareWithCasted = (AbstractUserOrGroupImpl) toShareWith;
    User sharer = TestFactory.createAnyUser("sharer");
    List<AbstractUserOrGroupImpl> groups = TransformerUtils.toList(toShareWithCasted);
    // if dao is empty then no way can already be shared!
    when(groupSharingDao.getUsersOrGroupsWithRecordAccess(doc.getId()))
        .thenReturn(Collections.EMPTY_LIST);
    assertTrue(sharingStatusImpl.canShare(toShareWith, doc, sharer));

    // if doc is already shared with this user, will return false:
    when(groupSharingDao.getUsersOrGroupsWithRecordAccess(doc.getId())).thenReturn(groups);
    assertFalse(sharingStatusImpl.canShare(toShareWith, doc, sharer));

    // now let's return a group that contains the user, we can still share with the user as well?
    // RSPAC 563
    AbstractUserOrGroupImpl grp = TestFactory.createAnyGroup(toShareWith, (User[]) null);
    groups = TransformerUtils.toList(grp);
    when(groupSharingDao.getUsersOrGroupsWithRecordAccess(doc.getId())).thenReturn(groups);
    assertTrue(sharingStatusImpl.canShare(toShareWith, doc, sharer));

    // and if we're already shared with an individual, we can still share
    // with a group containing that individual
    groups = TransformerUtils.toList(toShareWithCasted);
    when(groupSharingDao.getUsersOrGroupsWithRecordAccess(doc.getId())).thenReturn(groups);
    assertTrue(sharingStatusImpl.canShare(grp, doc, sharer));
  }
}
