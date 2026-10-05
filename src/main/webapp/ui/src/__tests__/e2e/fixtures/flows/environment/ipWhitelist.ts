import { randomBytes } from "node:crypto";
import type { IpWhitelistComponent } from "@/__tests__/e2e/components/system/config/IpWhitelistComponent";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";
import { withSysadminPage } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
import { SystemConfigPage } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

type IpWhitelistFixtures = {
  flowIpWhitelist: {
    whitelist: IpWhitelistComponent;
    // Registers the entry for teardown removal, so tests never touch entries they didn't add.
    uniqueIpAddress: (kind: "address" | "range") => string;
  };
};

const hexGroup = () => randomBytes(2).toString("hex");

export const test = apiTest.extend<IpWhitelistFixtures>({
  flowIpWhitelist: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("flowIpWhitelist");
    await withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => new SystemConfigPage(page),
      async (config) => {
        const created: string[] = [];
        const uniqueIpAddress = (kind: "address" | "range") => {
          // RFC 3849 documentation prefix: never a real client address, and too large to collide.
          const entry = kind === "range" ? `2001:db8:${hexGroup()}::/48` : `2001:db8::${hexGroup()}:${hexGroup()}`;
          created.push(entry);
          return entry;
        };
        try {
          await use({ whitelist: await config.openIpWhitelist(), uniqueIpAddress });
        } finally {
          const whitelist = await config.openIpWhitelist();
          for (const entry of created) {
            if ((await whitelist.row(entry).count()) > 0) {
              await whitelist.remove(entry);
            }
          }
        }
      },
    );
  },
});
