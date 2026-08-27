import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { AuditAction, AuditTrailPage } from "@/__tests__/e2e/pageObjects/myrspace/AuditTrailPage";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";

async function assertAuditAction(auditTrail: AuditTrailPage, globalId: string, action: AuditAction): Promise<void> {
  await auditTrail.open();
  await auditTrail.isLoaded();
  await auditTrail.filterByGlobalId(globalId);
  await auditTrail.checkAction(action);
  await auditTrail.submitQuery();
  await expect(auditTrail.resultRows).toHaveCount(1);
}

test.describe("Document signing and witnessing", { tag: tags.SYSTEM }, () => {
  test("As a group member, I can sign a document and have a fellow group member witness it", async ({
    clientSysadmin,
    flowUserSession,
    flowSysadminGroupAdmin,
  }) => {
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eWitnessPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eWitnessMember");
    await clientSysadmin.createGroup({
      displayName: `${member.username}Group`,
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    const memberSession = await flowUserSession(member.username, DYNAMIC_USER_PASSWORD);
    await memberSession.workspace.open();
    const editor = await memberSession.workspace.createBasicDocument();
    const documentView = await editor.saveAndView();
    // saveAndView() is a client-side transition that doesn't refresh the legacy toolbar Sign.
    await documentView.reload();
    const globalId = await documentView.header.getUniqueId();

    await documentView.sign(DYNAMIC_USER_PASSWORD, [`${pi.fullName} (${pi.username}@example.com)`]);
    await expect(documentView.statusText("Signed, awaiting witness")).toBeVisible();

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.workspace.open();
    const witnessView = await piSession.workspace.openMessageLinkedDocument("Untitled document");
    await witnessView.witness(DYNAMIC_USER_PASSWORD);

    await expect(witnessView.statusText("Signed and witnessed")).toBeVisible();

    const { auditTrail } = flowSysadminGroupAdmin;
    await assertAuditAction(auditTrail, globalId, "SIGN");
    await assertAuditAction(auditTrail, globalId, "WITNESSED");
  });
});
