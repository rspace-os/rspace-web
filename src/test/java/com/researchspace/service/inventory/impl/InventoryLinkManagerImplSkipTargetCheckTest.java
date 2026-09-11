package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.LinkTargetResolver;
import com.researchspace.service.inventory.LinkTargetSnapshotResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** CSV import stores dangling links; every other caller still needs a readable target. */
@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplSkipTargetCheckTest {

  @Mock private InventoryLinkDao linkDao;
  @Mock private LinkTargetResolver linkTargetResolver;
  @Mock private LinkTargetSnapshotResolver snapshotResolver;
  @InjectMocks private InventoryLinkManagerImpl manager;

  private final User actor = new User("importer");
  private ApiInventoryLink apiLink;

  @BeforeEach
  void setUp() {
    apiLink = new ApiInventoryLink();
    apiLink.setRelationType("Cites");
    apiLink.setTargetGlobalId("SA123");
  }

  @Test
  void skipTargetCheckStoresLinkWithoutResolvingTarget() {
    apiLink.setSkipTargetCheck(true);
    when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

    InventoryLink saved = manager.createLink(apiLink, actor);

    assertEquals("SA123", saved.getTargetGlobalId());
    assertEquals("Cites", saved.getRelationType());
    verify(linkTargetResolver, never()).targetExistsAndIsReadable(any(), any());
  }

  @Test
  void defaultStillRejectsUnresolvableTarget() {
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);

    assertThrows(ApiRuntimeException.class, () -> manager.createLink(apiLink, actor));
    verify(linkDao, never()).save(any());
  }
}
