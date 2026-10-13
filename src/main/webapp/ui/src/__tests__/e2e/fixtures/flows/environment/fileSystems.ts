import { NetFileSystemsClient } from "@/__tests__/e2e/api/clients/NetFileSystemsClient";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";
import { withSysadminPage } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import { SystemConfigPage } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";
import { trackedUniqueNames } from "@/__tests__/e2e/testData";

type FileSystemFixtures = {
  flowFileSystems: {
    config: SystemConfigPage;
    client: NetFileSystemsClient;
    /** Teardown deletes only file systems named by this. */
    uniqueFileSystemName: () => string;
  };
};

export const test = apiTest.extend<FileSystemFixtures>({
  flowFileSystems: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("flowFileSystems");
    await withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => ({ config: new SystemConfigPage(page), client: new NetFileSystemsClient(page.request) }),
      async ({ config, client }) => {
        const names = trackedUniqueNames("e2eFileSystem");
        try {
          await use({ config, client, uniqueFileSystemName: names.next });
        } finally {
          // Deleting works only while no filestore uses the file system; these tests never add one.
          for (const fileSystem of await client.list()) {
            if (names.created.has(fileSystem.name)) {
              await client.delete(fileSystem.id);
            }
          }
        }
      },
    );
  },
});
