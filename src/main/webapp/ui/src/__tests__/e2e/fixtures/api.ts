import type { APIRequestContext } from "@playwright/test";
import { request } from "@playwright/test";
import { DocumentsClient } from "../api/clients/DocumentsClient";
import { FilesClient } from "../api/clients/FilesClient";
import { FoldersClient } from "../api/clients/FoldersClient";
import { FormsClient } from "../api/clients/FormsClient";
import { InventoryClient } from "../api/clients/InventoryClient";
import { MailpitClient } from "../api/clients/MailpitClient";
import { ShareClient } from "../api/clients/ShareClient";
import { SnippetsClient } from "../api/clients/SnippetsClient";
import { StatusClient } from "../api/clients/StatusClient";
import { StoichiometryClient } from "../api/clients/StoichiometryClient";
import { SysadminClient } from "../api/clients/SysadminClient";
import { env } from "../env";
import { SYSADMIN } from "../users";
import { uiTest } from "./ui";

type ApiFixtures = {
  apiContext: APIRequestContext;
  clientDocuments: DocumentsClient;
  clientFiles: FilesClient;
  clientFolders: FoldersClient;
  clientForms: FormsClient;
  clientInventory: InventoryClient;
  clientSnippets: SnippetsClient;
  clientShare: ShareClient;
  clientStatus: StatusClient;
  clientStoichiometry: StoichiometryClient;
  clientSysadmin: SysadminClient;
  clientMailpit: MailpitClient;
};

export const apiTest = uiTest.extend<ApiFixtures>({
  // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture arg
  apiContext: async ({}, use) => {
    // Playwright request contexts inherit the project's storageState by default, which would
    // send the signed-in user's session cookie alongside the apiKey header. API clients must be
    // pure key clients, so start with an explicitly empty storage state.
    const context = await request.newContext({
      baseURL: env.baseURL,
      storageState: { cookies: [], origins: [] },
    });
    await use(context);
    await context.dispose();
  },
  clientDocuments: async ({ apiContext, appUser }, use) => {
    await use(new DocumentsClient(apiContext, appUser.apiKey));
  },
  clientFiles: async ({ apiContext, appUser }, use) => {
    await use(new FilesClient(apiContext, appUser.apiKey));
  },
  clientFolders: async ({ apiContext, appUser }, use) => {
    await use(new FoldersClient(apiContext, appUser.apiKey));
  },
  clientForms: async ({ apiContext, appUser }, use) => {
    await use(new FormsClient(apiContext, appUser.apiKey));
  },
  clientInventory: async ({ apiContext, appUser }, use) => {
    await use(new InventoryClient(apiContext, appUser.apiKey));
  },
  clientSnippets: async ({ page }, use) => {
    await use(new SnippetsClient(page));
  },
  clientShare: async ({ apiContext, appUser }, use) => {
    await use(new ShareClient(apiContext, appUser.apiKey));
  },
  clientStatus: async ({ apiContext, appUser }, use) => {
    await use(new StatusClient(apiContext, appUser.apiKey));
  },
  clientStoichiometry: async ({ apiContext, appUser }, use) => {
    await use(new StoichiometryClient(apiContext, appUser.apiKey));
  },
  clientSysadmin: async ({ apiContext }, use) => {
    const client = new SysadminClient(apiContext, SYSADMIN.apiKey);
    let cleanupError: Error | undefined;
    try {
      await use(client);
    } finally {
      // allSettled: one failed disable must not hide another's failure. Users a scenario already
      // deleted itself are excluded via pendingCleanup.
      const pending = client.pendingCleanup;
      const results = await Promise.allSettled(pending.map((user) => client.disableUser(user.id)));
      const failures = results
        .map((result, index) => ({ result, user: pending[index] }))
        .filter(
          (entry): entry is typeof entry & { result: PromiseRejectedResult } => entry.result.status === "rejected",
        );
      for (const { result, user } of failures) {
        console.error(`Failed to disable e2e user "${user.username}" (id ${user.id}) during teardown:`, result.reason);
      }
      if (failures.length > 0) {
        cleanupError = new Error(
          `${failures.length} e2e user(s) failed cleanup: ${failures.map(({ user }) => user.username).join(", ")}`,
        );
      }
    }
    if (cleanupError) {
      throw cleanupError;
    }
  },

  // biome-ignore lint/correctness/noEmptyPattern: Playwright requires destructuring pattern for fixture arg
  clientMailpit: async ({}, use) => {
    const context = await request.newContext({ baseURL: env.mailpitBaseUrl });
    await use(new MailpitClient(context));
    await context.dispose();
  },
});
