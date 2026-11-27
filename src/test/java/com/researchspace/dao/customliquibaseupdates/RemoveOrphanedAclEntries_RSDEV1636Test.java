package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoveOrphanedAclEntries_RSDEV1636Test extends SpringTransactionalTest {

  @Test
  void removesOrphanedEntriesWithBackupAndKeepsEveryExistingPrincipal() throws Exception {
    User pi = createAndSaveAPi();
    User alice = createAndSaveUserIfNotExists(getRandomAlphabeticString("acl") + "e");
    initialiseContentWithEmptyContent(pi, alice);
    String lab = createGroup(getRandomAlphabeticString("lab"), pi).getUniqueName();
    Group projectGroup = new Group(getRandomAlphabeticString("project"), pi);
    projectGroup.setGroupType(GroupType.PROJECT_GROUP);
    projectGroup.setDisplayName("project");
    String project = grpMgr.saveGroup(projectGroup, pi).getUniqueName();
    Group collabGroup = new Group(getRandomAlphabeticString("collab"), pi);
    collabGroup.setGroupType(GroupType.COLLABORATION_GROUP);
    collabGroup.setDisplayName("collab");
    String collab = grpMgr.saveGroup(collabGroup, pi).getUniqueName();
    Group bracketGroup = new Group(getRandomAlphabeticString("odd") + "[name]", pi);
    bracketGroup.setDisplayName("odd");
    String bracketed = grpMgr.saveGroup(bracketGroup, pi).getUniqueName();
    Community community = new Community();
    community.addAdmin(
        createAndSaveUserIfNotExists(getRandomAlphabeticString("admin"), "ROLE_ADMIN"));
    community.setUniqueName(getRandomAlphabeticString("comm"));
    community.setDisplayName("comm");
    community = communityMgr.save(community);
    String a = alice.getUsername();
    String accented = a.substring(0, a.length() - 1) + "é";
    String ghost = getRandomAlphabeticString("ghost");

    String keptAcl =
        String.join(
            "&",
            a + "=RECORD:READ:",
            lab + "=RECORD:READ:",
            collab + "=RECORD:WRITE:",
            project + "=RECORD:READ:",
            community.getUniqueName() + "=RECORD:READ:",
            lab + "[PI,RS_LAB_ADMIN]=RECORD:READ:",
            ghost + "[PI]=RECORD:READ:",
            bracketed + "=RECORD:READ:",
            "rspace anonymous guest=RECORD:READ:",
            a.toUpperCase() + "=RECORD:READ:",
            accented + "=RECORD:READ:",
            a + " =RECORD:READ:");
    StructuredDocument cleaned = createBasicDocumentInRootFolderWithText(pi, "cleaned");
    String oldAcl = ghost + "=RECORD:READ:&" + keptAcl + "&" + lab + "x=RECORD:READ:";
    storeAcl(cleaned.getId(), oldAcl);
    StructuredDocument untouched = createBasicDocumentInRootFolderWithText(pi, "untouched");
    storeAcl(untouched.getId(), keptAcl);
    StructuredDocument unparsed = createBasicDocumentInRootFolderWithText(pi, "unparsed");
    String unparsedAcl = ghost + "=RECORD:READ:&junk";
    storeAcl(unparsed.getId(), unparsedAcl);

    String message = runChange().getConfirmationMessage();

    assertEquals(keptAcl, aclColumn(cleaned.getId()));
    assertEquals(keptAcl, aclColumn(untouched.getId()));
    assertEquals(unparsedAcl, aclColumn(unparsed.getId()));
    assertEquals(List.of(oldAcl, keptAcl, 2), backup(cleaned.getId()));
    assertEquals(0, backupRows(untouched.getId()) + backupRows(unparsed.getId()));
    assertTrue(message.contains("removed 2 orphaned entries"), message);

    String second = runChange().getConfirmationMessage();
    assertTrue(second.contains("changed 0"), second);
    assertEquals(keptAcl, aclColumn(cleaned.getId()));
    assertEquals(1, backupRows(cleaned.getId()));
  }

  // only the orphan goes; a kept element is written back byte for byte, even with a permission
  // tail the permission parser ignores
  @Test
  void keepsAnElementWithAMalformedPermissionTailByteForByte() throws Exception {
    User pi = createAndSaveAPi();
    initialiseContentWithEmptyContent(pi);
    String kept = pi.getUsername() + "=RECORD:READ:garbage";
    String oldAcl = getRandomAlphabeticString("ghost") + "=RECORD:READ:&" + kept;
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi, "tail");
    storeAcl(doc.getId(), oldAcl);

    runChange();

    assertEquals(kept, aclColumn(doc.getId()));
    assertEquals(List.of(oldAcl, kept, 1), backup(doc.getId()));
  }

  private RemoveOrphanedAclEntries_RSDEV1636 runChange() {
    RemoveOrphanedAclEntries_RSDEV1636 change = new RemoveOrphanedAclEntries_RSDEV1636();
    change.context = applicationContext;
    change.sessionFactory = sessionFactory;
    change.addBeans();
    change.doExecute(null);
    return change;
  }

  private void storeAcl(Long id, String acl) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery("update BaseRecord set acl = :acl where id = :id")
        .setParameter("acl", acl)
        .setParameter("id", id)
        .executeUpdate();
  }

  private String aclColumn(Long id) {
    return sessionFactory
        .getCurrentSession()
        .createNativeQuery("select acl from BaseRecord where id = :id", String.class)
        .setParameter("id", id)
        .uniqueResult();
  }

  private List<Object> backup(Long id) {
    Object[] row =
        sessionFactory
            .getCurrentSession()
            .createNativeQuery(
                "select old_acl, new_acl, removed_count from "
                    + RemoveOrphanedAclEntries_RSDEV1636.BACKUP_TABLE
                    + " where record_id = :id",
                Object[].class)
            .setParameter("id", id)
            .uniqueResult();
    return List.of(row[0], row[1], ((Number) row[2]).intValue());
  }

  private int backupRows(Long id) {
    return ((Number)
            sessionFactory
                .getCurrentSession()
                .createNativeQuery(
                    "select count(*) from "
                        + RemoveOrphanedAclEntries_RSDEV1636.BACKUP_TABLE
                        + " where record_id = :id",
                    Long.class)
                .setParameter("id", id)
                .uniqueResult())
        .intValue();
  }
}
