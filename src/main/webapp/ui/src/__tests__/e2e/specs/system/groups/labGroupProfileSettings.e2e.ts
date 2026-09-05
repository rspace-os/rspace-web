import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Lab Group profile settings", { tag: tags.SYSTEM }, () => {
  test("As a PI, I can allow myself to edit all work in my Lab Group", async ({
    flowSysadminConfig,
    clientSysadmin,
    flowUserSession,
  }) => {
    await flowSysadminConfig.ensureSetting("pi_can_edit_all_work_in_labgroup", "ALLOWED");

    const pi = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eProfilePi");
    const group = await clientSysadmin.createGroup({
      displayName: uniqueName("e2eProfileGroup"),
      type: "LAB_GROUP",
      users: [{ username: pi.username, roleInGroup: "PI" }],
    });

    const piSession = await flowUserSession(pi.username, DYNAMIC_USER_PASSWORD);
    await piSession.groupDetails.openGroup(group.id);
    await piSession.groupDetails.setPiCanEditAllWork(true);

    await piSession.groupDetails.editProfileButton.click();
    await expect(piSession.groupDetails.piCanEditAllWorkCheckbox).toBeChecked();
  });
});
