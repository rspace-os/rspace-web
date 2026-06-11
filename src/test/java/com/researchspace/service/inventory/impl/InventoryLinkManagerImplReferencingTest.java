package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplReferencingTest {

  @Mock private InventoryLinkDao linkDao;
  @Mock private InventoryPermissionUtils permissionUtils;
  @Mock private LinkTargetResolver linkTargetResolver;
  @InjectMocks private InventoryLinkManagerImpl manager;

  private User actor;

  @BeforeEach
  void setUp() {
    actor = new User("viewer");
    // these tests cover the source query, so the target read-permission gate is open;
    // InventoryLinkManagerImplUnitTest covers the gate itself
    lenient().when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
  }

  @Test
  void queriesByParsedPrefixAndDbIdForElnTarget() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(Collections.emptyList());

    assertTrue(manager.findReferencingItems("SD123", actor).isEmpty());

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SD, 123L);
  }

  @Test
  void stripsVersionSuffixSoPinnedLinksMatchTheBaseRecord() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SD, 123L))
        .thenReturn(Collections.emptyList());

    manager.findReferencingItems("SD123v5", actor);

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SD, 123L);
  }

  @Test
  void queriesByPrefixAndDbIdForInventoryTarget() {
    when(linkDao.findReferencingLinkFields(GlobalIdPrefix.SA, 42L))
        .thenReturn(Collections.emptyList());

    assertEquals(0, manager.findReferencingItems("SA42", actor).size());

    verify(linkDao).findReferencingLinkFields(GlobalIdPrefix.SA, 42L);
  }
}
