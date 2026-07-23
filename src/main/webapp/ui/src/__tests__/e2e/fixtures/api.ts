import type { APIRequestContext } from "@playwright/test";
import { request } from "@playwright/test";
import { DocumentsClient } from "../api/clients/DocumentsClient";
import { FilesClient } from "../api/clients/FilesClient";
import { InventoryClient } from "../api/clients/InventoryClient";
import { SysadminClient } from "../api/clients/SysadminClient";
import { env } from "../env";
import { SYSADMIN } from "../users";
import { uiTest } from "./ui";

type ApiFixtures = {
  apiContext: APIRequestContext;
  clientDocuments: DocumentsClient;
  clientFiles: FilesClient;
  clientInventory: InventoryClient;
  clientSysadmin: SysadminClient;
};

export const apiTest = uiTest.extend<ApiFixtures>({
  // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture arg
  apiContext: async ({}, use) => {
    const context = await request.newContext({ baseURL: env.baseURL });
    await use(context);
    await context.dispose();
  },
  clientDocuments: async ({ apiContext, appUser }, use) => {
    await use(new DocumentsClient(apiContext, appUser.apiKey));
  },
  clientFiles: async ({ apiContext, appUser }, use) => {
    await use(new FilesClient(apiContext, appUser.apiKey));
  },
  clientInventory: async ({ apiContext, appUser }, use) => {
    await use(new InventoryClient(apiContext, appUser.apiKey));
  },
  clientSysadmin: async ({ apiContext }, use) => {
    const client = new SysadminClient(apiContext, SYSADMIN.apiKey);
    try {
      await use(client);
    } finally {
      // allSettled: one failed disable must not hide another's failure, or the test's own.
      const results = await Promise.allSettled(client.createdUserIds.toReversed().map((id) => client.disableUser(id)));
      for (const result of results) {
        if (result.status === "rejected") {
          console.error("Failed to disable e2e user during teardown:", result.reason);
        }
      }
    }
  },
});
