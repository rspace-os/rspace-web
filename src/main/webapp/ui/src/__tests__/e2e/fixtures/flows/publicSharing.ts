import type { BrowserContext } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sysadminSessions";
import { PublicDocumentPage } from "@/__tests__/e2e/pageObjects/myrspace/PublicDocumentPage";
import type { SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";

type PublicSharingFixtures = {
  flowPublicSharing: undefined;
  flowOpenAnonymousDocument: (href: string) => Promise<PublicDocumentPage>;
};

export const test = sysadminSessionTest.extend<PublicSharingFixtures>({
  flowPublicSharing: async ({ flowSysadminConfig }, use) => {
    env.assertGlobalMutationsAllowed("flowPublicSharing");
    const settingNames = ["public_sharing", "publicdocs_allow_seo"] as const;
    const originalValues = new Map<string, SystemPropertyValue>();
    for (const name of settingNames) {
      originalValues.set(name, (await flowSysadminConfig.getSetting(name)).trim() as SystemPropertyValue);
    }
    try {
      await flowSysadminConfig.ensureSettings({
        public_sharing: "ALLOWED",
        publicdocs_allow_seo: "ALLOWED",
      });
      await use(undefined);
    } finally {
      for (const [name, value] of originalValues) {
        await flowSysadminConfig.ensureSetting(name, value);
      }
    }
  },

  flowOpenAnonymousDocument: async ({ browser, browserContextOptions }, use) => {
    const contexts: BrowserContext[] = [];
    try {
      await use(async (href) => {
        const context = await browser.newContext({ ...browserContextOptions, storageState: undefined });
        contexts.push(context);
        const publicPage = new PublicDocumentPage(await context.newPage());
        await publicPage.openAt(href);
        return publicPage;
      });
    } finally {
      await Promise.all(contexts.map((context) => context.close()));
    }
  },
});
