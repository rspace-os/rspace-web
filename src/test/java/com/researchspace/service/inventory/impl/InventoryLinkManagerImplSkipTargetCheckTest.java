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

/**
 * CSV import stores a link whatever state its target is in; the card reports an unresolvable target
 * as "No access" at read time. Every other caller still needs a target it can read.
 */
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
  void importStoresLinkWhoseTargetDoesNotResolve() {
    // missing and unreadable are one case here: the importer cannot tell them apart and must
    // not try, so neither fails the row
    apiLink.setSkipTargetCheck(true);
    when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

    InventoryLink saved = manager.createLink(apiLink, actor);

    assertEquals("SA123", saved.getTargetGlobalId());
    assertEquals("Cites", saved.getRelationType());
    verify(linkTargetResolver, never()).targetExistsAndIsReadable(any(), any());
  }

  @Test
  void updateStoresLinkWhoseTargetDoesNotResolveWhenImporting() {
    apiLink.setSkipTargetCheck(true);
    when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

    InventoryLink saved = manager.updateLink(new InventoryLink(), apiLink, actor);

    assertEquals("SA123", saved.getTargetGlobalId());
  }

  @Test
  void createStillRejectsUnresolvableTargetForOrdinaryCallers() {
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);

    assertThrows(ApiRuntimeException.class, () -> manager.createLink(apiLink, actor));
    verify(linkDao, never()).save(any());
  }

  @Test
  void updateStillRejectsUnresolvableTargetForOrdinaryCallers() {
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(false);

    assertThrows(
        ApiRuntimeException.class, () -> manager.updateLink(new InventoryLink(), apiLink, actor));
    verify(linkDao, never()).save(any());
  }

  @Test
  void ordinaryCallerWithReadableTargetStoresTheLink() {
    when(linkTargetResolver.targetExistsAndIsReadable(any(), any())).thenReturn(true);
    when(linkDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertEquals("SA123", manager.createLink(apiLink, actor).getTargetGlobalId());
  }
}
