import { storageStatePath } from "@/__tests__/e2e/authState";
import { AnnouncementToastComponent } from "@/__tests__/e2e/components/shared/AnnouncementToastComponent";
import { NotificationsDialogComponent } from "@/__tests__/e2e/components/shared/NotificationsDialogComponent";
import { SendMessageDialogComponent } from "@/__tests__/e2e/components/shared/SendMessageDialogComponent";
import { env } from "@/__tests__/e2e/env";
import { DocumentPage } from "@/__tests__/e2e/pageObjects/document/DocumentPage";
import { DashboardPage } from "@/__tests__/e2e/pageObjects/messaging/DashboardPage";
import { NotebookPage } from "@/__tests__/e2e/pageObjects/notebook/NotebookPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { SYSADMIN } from "@/__tests__/e2e/users";
import { loginInNewContext, test as userSessionTest } from "./sessions/userSessions";

export type MessagingSession = {
  workspace: WorkspacePage;
  sendMessage: SendMessageDialogComponent;
  notifications: NotificationsDialogComponent;
  announcement: AnnouncementToastComponent;
  notebook: NotebookPage;
  document: DocumentPage;
};

type MessagingFixtures = {
  flowMessagingSession: (username: string, password: string) => Promise<MessagingSession>;
  flowSysadminAnnouncement: (marker: string, markup?: string) => Promise<void>;
};

export const test = userSessionTest.extend<MessagingFixtures>({
  flowMessagingSession: async ({ browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (username, password) => {
        const { page, close } = await loginInNewContext(browser, browserContextOptions, username, password);
        closers.push(close);
        return {
          workspace: new WorkspacePage(page),
          sendMessage: new SendMessageDialogComponent(page),
          notifications: new NotificationsDialogComponent(page),
          announcement: new AnnouncementToastComponent(page),
          notebook: new NotebookPage(page),
          document: new DocumentPage(page),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },

  flowSysadminAnnouncement: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("sendGlobalMessage");
    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    const dashboard = new DashboardPage(await ctx.newPage());
    const sent: Array<string> = [];
    try {
      await use(async (marker, markup = "") => {
        sent.push(marker);
        await dashboard.open();
        const dialog = await dashboard.openCreateMessage();
        await dialog.send({ type: "Message to all users", text: `${marker} ${markup}`.trim() });
      });
    } finally {
      try {
        if (sent.length > 0) {
          await dashboard.open();
          await dashboard.openSentRequests();
          for (const marker of sent) {
            if ((await dashboard.sentRequest(marker).count()) > 0) {
              await dashboard.cancelSentRequestQuietly(marker);
            }
          }
        }
      } finally {
        await ctx.close();
      }
    }
  },
});
