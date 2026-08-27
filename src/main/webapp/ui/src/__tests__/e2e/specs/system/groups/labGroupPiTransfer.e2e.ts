import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Lab Group PI transfer", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, changing a Lab Group's PI is blocked when no member could become PI", async ({
    flowSysadminGroupAdmin,
    clientSysadmin,
  }) => {
    const { groupDetails } = flowSysadminGroupAdmin;
    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePiTransferPi");
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2ePiTransferMember");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2ePiTransferGroup"),
      type: "LAB_GROUP",
      users: [
        { username: pi.username, roleInGroup: "PI" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    await groupDetails.openGroup(group.id);
    const alert = await groupDetails.clickChangePiExpectingNoCandidate();

    await expect(alert.message).toContainText("There is no-one in the group who could become a new PI.");
    await alert.confirm();
  });
});
