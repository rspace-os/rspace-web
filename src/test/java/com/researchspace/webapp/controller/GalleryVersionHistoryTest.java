package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.GalleryVersionHistory;
import com.researchspace.service.AuditManager;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.Date;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pure unit tests for the Gallery version-history endpoint, with a mocked {@link AuditManager},
 * {@link BaseRecordManager} and {@link UserManager}. Envers-backed behaviour is covered elsewhere.
 */
public class GalleryVersionHistoryTest {

  private static final long MEDIA_ID = 42L;

  private final AuditManager auditManager = Mockito.mock(AuditManager.class);
  private final BaseRecordManager baseRecordManager = Mockito.mock(BaseRecordManager.class);
  private final UserManager userManager = Mockito.mock(UserManager.class);
  private final GalleryController controller = new GalleryController();
  private final User user = TestFactory.createAnyUser("versionHistoryUser");

  @BeforeEach
  public void setUp() {
    controller.setUserManager(userManager);
    controller.setBaseRecordMgr(baseRecordManager);
    // BaseController exposes auditManager as a protected field, and this test shares its package
    controller.auditManager = auditManager;
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
  }

  private EcatMediaFile mediaAtVersion(long version, long size, String modifiedBy, Date modified) {
    return mediaAtVersion(version, size, modifiedBy, modified, "assay.wav", "a recording");
  }

  private EcatMediaFile mediaAtVersion(
      long version, long size, String modifiedBy, Date modified, String name, String description) {
    EcatAudio media = TestFactory.createEcatAudio(MEDIA_ID, user);
    media.setVersion(version);
    media.setSize(size);
    media.setModifiedBy(modifiedBy);
    media.setModificationDate(modified);
    media.setName(name);
    media.setDescription(description);
    return media;
  }

  @Test
  public void listsEveryRevisionNewestVersionLast() {
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(2L, 200L, user.getUsername(), new Date()));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(
            List.of(
                new AuditedEntity<>(mediaAtVersion(1L, 100L, "alice", new Date()), 10L),
                new AuditedEntity<>(mediaAtVersion(2L, 200L, "alice", new Date()), 20L)));
    when(userManager.getFullNameByUsername("alice")).thenReturn("Alice Smith");

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertEquals(2, history.revisionsCount());
    assertEquals(2, history.revisions().size());
    assertEquals(10L, history.revisions().get(0).revisionId());
    assertEquals(Long.valueOf(1L), history.revisions().get(0).item().version());
    assertEquals(20L, history.revisions().get(1).revisionId());
    assertEquals(Long.valueOf(2L), history.revisions().get(1).item().version());
  }

  @Test
  public void reportsSizeAtEachRevisionSoUsersCanTellVersionsApart() {
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(2L, 2048L, user.getUsername(), new Date()));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(
            List.of(
                new AuditedEntity<>(mediaAtVersion(1L, 918L, "alice", new Date()), 10L),
                new AuditedEntity<>(mediaAtVersion(2L, 2048L, "alice", new Date()), 20L)));

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertEquals(918L, history.revisions().get(0).item().size());
    assertEquals(2048L, history.revisions().get(1).item().size());
  }

  @Test
  public void reportsTheNameAndDescriptionEachVersionCarried() {
    /*
     * Both are per-revision, not per-item: a new version can be a differently named file, and
     * either can be edited at any time. Reporting the live values would put today's name and
     * caption beside an older version's content.
     */
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(2L, 200L, "alice", new Date(), "final.wav", "the final take"));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(
            List.of(
                new AuditedEntity<>(
                    mediaAtVersion(1L, 100L, "alice", new Date(), "first-draft.wav", "a rough cut"),
                    10L),
                new AuditedEntity<>(
                    mediaAtVersion(2L, 200L, "alice", new Date(), "final.wav", "the final take"),
                    20L)));

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertEquals("first-draft.wav", history.revisions().get(0).item().name());
    assertEquals("a rough cut", history.revisions().get(0).item().description());
    assertEquals("final.wav", history.revisions().get(1).item().name());
    assertEquals("the final take", history.revisions().get(1).item().description());
  }

  @Test
  public void aVersionWithNoDescriptionReportsNoneRatherThanTheLiveOne() {
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(2L, 200L, "alice", new Date(), "final.wav", "the final take"));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(
            List.of(
                new AuditedEntity<>(
                    mediaAtVersion(1L, 100L, "alice", new Date(), "first-draft.wav", null), 10L)));

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertNull(history.revisions().get(0).item().description());
  }

  @Test
  public void resolvesTheEditorsFullNameOncePerUsername() {
    // the "By" column shows a full name, but an audited record only knows the username
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(3L, 100L, "alice", new Date()));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(
            List.of(
                new AuditedEntity<>(mediaAtVersion(1L, 100L, "alice", new Date()), 10L),
                new AuditedEntity<>(mediaAtVersion(2L, 100L, "alice", new Date()), 20L),
                new AuditedEntity<>(mediaAtVersion(3L, 100L, "alice", new Date()), 30L)));
    when(userManager.getFullNameByUsername("alice")).thenReturn("Alice Smith");

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertTrue(
        history.revisions().stream()
            .allMatch(r -> "Alice Smith".equals(r.item().modifiedByFullName())));
    // three revisions by the same user must not mean three lookups
    verify(userManager, Mockito.times(1)).getFullNameByUsername("alice");
  }

  @Test
  public void emitsModificationDateAsAnIsoInstant() {
    // the frontend parses this with Date.parse, so it has to be ISO-8601
    Date modified = new Date(1_700_000_000_000L);
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(1L, 100L, "alice", modified));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID))
        .thenReturn(List.of(new AuditedEntity<>(mediaAtVersion(1L, 100L, "alice", modified), 10L)));

    GalleryVersionHistory history = controller.getVersionHistory(MEDIA_ID).getData();

    assertEquals("2023-11-14T22:13:20Z", history.revisions().get(0).item().lastModified());
  }

  @Test
  public void anItemWithNoAuditRowsYieldsAnEmptyHistoryRatherThanAnError() {
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenReturn(mediaAtVersion(1L, 100L, "alice", new Date()));
    when(auditManager.getRevisionsForEntity(EcatMediaFile.class, MEDIA_ID)).thenReturn(List.of());

    AjaxReturnObject<GalleryVersionHistory> response = controller.getVersionHistory(MEDIA_ID);

    assertNull(response.getErrorMsg());
    assertEquals(0, response.getData().revisionsCount());
    assertTrue(response.getData().revisions().isEmpty());
  }

  @Test
  public void anUnreadableItemIsRefusedBeforeAnyAuditQuery() {
    when(baseRecordManager.retrieveMediaFile(user, MEDIA_ID))
        .thenThrow(new AuthorizationException("not authorized"));

    assertThrows(AuthorizationException.class, () -> controller.getVersionHistory(MEDIA_ID));

    Mockito.verifyNoInteractions(auditManager);
  }
}
