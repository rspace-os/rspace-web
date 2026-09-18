package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The existence probe the CSV importer's leniency turns on: only a kind we can check without a
 * permission decision, and only when the record really is gone.
 */
@ExtendWith(MockitoExtension.class)
class LinkTargetResolverImplKnownMissingTest {

  @Mock private InventoryPermissionUtils inventoryPermissionUtils;
  @InjectMocks private LinkTargetResolverImpl resolver;

  @Test
  void inventoryTargetWithNoRecordIsKnownMissing() {
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(any()))
        .thenThrow(new NotFoundException("gone"));

    assertTrue(resolver.targetIsKnownMissing(new GlobalIdentifier(GlobalIdPrefix.SA, 1L)));
  }

  @Test
  void existingInventoryTargetIsNotKnownMissingEvenWhenUnreadable() {
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(any()))
        .thenReturn(mock(Sample.class));

    assertFalse(resolver.targetIsKnownMissing(new GlobalIdentifier(GlobalIdPrefix.SA, 1L)));
  }

  @Test
  void elnTargetIsNeverKnownMissing() {
    // probing an ELN record's existence without a permission check is the disclosure
    // ADR-0002 forbids, so the lenient import path never applies to one
    assertFalse(resolver.targetIsKnownMissing(new GlobalIdentifier(GlobalIdPrefix.SD, 1L)));
  }

  @Test
  void versionSuffixIsIgnored() {
    when(inventoryPermissionUtils.getInvRecByGlobalIdOrThrowNotFoundException(any()))
        .thenThrow(new NotFoundException("gone"));

    assertTrue(resolver.targetIsKnownMissing(new GlobalIdentifier("SA1v3")));
  }

  @Test
  void nullTargetIsNotKnownMissing() {
    assertFalse(resolver.targetIsKnownMissing(null));
  }
}
