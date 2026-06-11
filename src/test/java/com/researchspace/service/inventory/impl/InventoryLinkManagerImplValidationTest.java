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
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.LinkTargetResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryLinkManagerImplValidationTest {

  @Mock private InventoryLinkDao linkDao;
  @Mock private InventoryPermissionUtils permissionUtils;
  @Mock private LinkTargetResolver linkTargetResolver;
  @InjectMocks private InventoryLinkManagerImpl manager;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("actor");
  }

  private void targetReadable(boolean readable) {
    when(linkTargetResolver.targetExistsAndIsReadable(any(GlobalIdentifier.class), any(User.class)))
        .thenReturn(readable);
  }

  private ApiInventoryLink apiLink(String relationType, String targetGlobalId) {
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType(relationType);
    api.setTargetGlobalId(targetGlobalId);
    return api;
  }

  @Test
  void createLinkRejectsMalformedTargetWithCleanError() {
    // the validator can be bypassed (e.g. an extra-field update omitting
    // "type"), so the manager itself must reject bad payloads with a clean
    // 422 error instead of letting a raw parse exception become a 500
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.createLink(apiLink("References", "not-a-gid"), user));
    assertEquals("errors.inventory.field.link.targetNotFound", ex.getErrorCode());
    verify(linkDao, never()).save(any());
  }

  @Test
  void createLinkRejectsUnsupportedTargetKind() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.createLink(apiLink("References", "FL12"), user));
    assertEquals("errors.inventory.field.link.targetKindUnsupported", ex.getErrorCode());
    verify(linkDao, never()).save(any());
  }

  @Test
  void createLinkRejectsRelationTypeOutsideDataCiteVocabulary() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.createLink(apiLink("NotARelation", "SD123"), user));
    assertEquals("errors.inventory.field.link.relationTypeInvalid", ex.getErrorCode());
    verify(linkDao, never()).save(any());
  }

  @Test
  void updateLinkRejectsMalformedTargetWithCleanError() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.updateLink(new InventoryLink(), apiLink("References", "ZZ99"), user));
    // ZZ is not a known prefix, so the id fails to parse at all
    assertEquals("errors.inventory.field.link.targetNotFound", ex.getErrorCode());
    verify(linkDao, never()).save(any());
  }

  @Test
  void createLinkPersistsWhenTargetReadable() {
    targetReadable(true);
    when(linkDao.save(any(InventoryLink.class))).thenAnswer(inv -> inv.getArgument(0));

    InventoryLink saved = manager.createLink(apiLink("References", "SD123"), user);

    assertEquals(GlobalIdPrefix.SD, saved.getTargetPrefix());
    assertEquals(Long.valueOf(123), saved.getTargetDbId());
    verify(linkDao).save(any(InventoryLink.class));
  }

  @Test
  void createLinkRejectsUnreadableTargetWithI18nCodeAndDoesNotPersist() {
    targetReadable(false);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.createLink(apiLink("References", "SD404"), user));

    assertEquals("errors.inventory.field.link.targetNotFound", ex.getErrorCode());
    verify(linkDao, never()).save(any(InventoryLink.class));
  }

  @Test
  void updateLinkPersistsWhenTargetReadable() {
    targetReadable(true);
    when(linkDao.save(any(InventoryLink.class))).thenAnswer(inv -> inv.getArgument(0));
    InventoryLink existing = new InventoryLink();

    InventoryLink updated = manager.updateLink(existing, apiLink("IsCalibratedBy", "NB9"), user);

    assertEquals("IsCalibratedBy", updated.getRelationType());
    assertEquals(GlobalIdPrefix.NB, updated.getTargetPrefix());
  }

  @Test
  void updateLinkRejectsUnreadableTargetAndDoesNotPersist() {
    targetReadable(false);
    InventoryLink existing = new InventoryLink();

    assertThrows(
        ApiRuntimeException.class,
        () -> manager.updateLink(existing, apiLink("References", "SD404"), user));

    verify(linkDao, never()).save(any(InventoryLink.class));
  }
}
