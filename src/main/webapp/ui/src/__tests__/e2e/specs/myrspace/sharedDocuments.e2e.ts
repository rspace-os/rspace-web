import { expect } from "@playwright/test";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

async function createLabGroup(
  clientSysadmin: SysadminClient,
  piUsername: string,
  memberUsername: string,
  displayNamePrefix: string,
) {
  return clientSysadmin.createGroup({
    displayName: uniqueName(displayNamePrefix),
    type: "LAB_GROUP",
    users: [
      { username: piUsername, roleInGroup: "PI" },
      { username: memberUsername, roleInGroup: "DEFAULT" },
    ],
  });
}

test("As a user, I can search, sort, page, and inspect documents shared with my group", async ({
  appUser,
  clientDocuments,
  clientShare,
  clientSysadmin,
  flowCreateUser,
  pageMyRSpace,
}) => {
  const member = await flowCreateUser("ROLE_USER", "e2eSharedMember");
  const group = await createLabGroup(clientSysadmin, appUser.username, member.username, "e2e-shared-group");
  const marker = uniqueName("shared-document");
  const documents = await Promise.all(
    Array.from({ length: 11 }, (_, index) => clientDocuments.create({ name: `${index}-${marker}` })),
  );
  await clientShare.shareWithGroup(
    documents.map((document) => document.id),
    group.id,
    "EDIT",
  );

  await pageMyRSpace.open();
  const shared = await pageMyRSpace.openSharedDocuments();
  await shared.search(marker);
  expect(await shared.rowCount()).toBe(10);

  await shared.sortByDocumentName();
  await expect
    .poll(() => shared.documentNames())
    .toEqual(["0", "1", "10", "2", "3", "4", "5", "6", "7", "8"].map((index) => `${index}-${marker}`));
  await shared.sortByDocumentName();
  await expect
    .poll(() => shared.documentNames())
    .toEqual(["9", "8", "7", "6", "5", "4", "3", "2", "10", "1"].map((index) => `${index}-${marker}`));

  await shared.nextPage();
  await expect.poll(() => shared.rowCount()).toBe(1);
  const info = await shared.openRecordInfo((await shared.documentNames())[0]);
  await expect(info.root).toBeVisible();
  await info.close();
});

test("As a user, I can filter shared documents by shared-with group, by unique ID, and inspect every row", async ({
  appUser,
  clientDocuments,
  clientShare,
  clientSysadmin,
  flowCreateUser,
  pageMyRSpace,
}) => {
  const member = await flowCreateUser("ROLE_USER", "e2eSharedFilterMember");
  const group = await createLabGroup(clientSysadmin, appUser.username, member.username, "e2e-shared-filter-group");
  const marker = uniqueName("shared-filter-doc");
  const doc1 = await clientDocuments.create({ name: `1-${marker}` });
  const doc2 = await clientDocuments.create({ name: `2-${marker}` });
  await clientShare.shareWithGroup([doc1.id, doc2.id], group.id, "EDIT");

  await pageMyRSpace.open();
  const shared = await pageMyRSpace.openSharedDocuments();
  await shared.search(marker);
  await expect.poll(() => shared.rowCount()).toBe(2);

  await shared.search(group.name);
  await expect.poll(() => shared.rowCount()).toBe(2);
  expect(await shared.sharedWithAt(0)).toBe(group.name);
  expect(await shared.sharedWithAt(1)).toBe(group.name);

  const uniqueId = await shared.uniqueIdAt(0);
  await shared.search(uniqueId);
  await expect.poll(() => shared.rowCount()).toBe(1);
  expect(await shared.uniqueIdAt(0)).toBe(uniqueId);

  await shared.search(marker);
  await expect.poll(() => shared.rowCount()).toBe(2);
  for (const name of await shared.documentNames()) {
    const info = await shared.openRecordInfo(name);
    await expect(info.root).toBeVisible();
    await info.close();
  }
});
