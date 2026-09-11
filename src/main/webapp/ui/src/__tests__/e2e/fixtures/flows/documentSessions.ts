import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { FoldersClient } from "@/__tests__/e2e/api/clients/FoldersClient";
import { DocumentEditorPage } from "@/__tests__/e2e/pageObjects/document/DocumentEditorPage";
import { DocumentPage } from "@/__tests__/e2e/pageObjects/document/DocumentPage";
import { SharedDocumentsPage } from "@/__tests__/e2e/pageObjects/myrspace/SharedDocumentsPage";
import { NotebookPage } from "@/__tests__/e2e/pageObjects/notebook/NotebookPage";
import { DirectoryPage } from "@/__tests__/e2e/pageObjects/system/groups/DirectoryPage";
import { GroupDetailsPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupDetailsPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import type { AppUser } from "@/__tests__/e2e/users";
import { loginInNewContext, refreshOwnSessionAfterGroupChange, test as userSessionTest } from "./sessions/userSessions";

type DocumentUser = Pick<AppUser, "username" | "password" | "apiKey">;

export type DocumentSession = {
  workspace: WorkspacePage;
  document: DocumentPage;
  editor: DocumentEditorPage;
  notebook: NotebookPage;
  sharedDocuments: SharedDocumentsPage;
  groupDetails: GroupDetailsPage;
  directory: DirectoryPage;
  documents: DocumentsClient;
  folders: FoldersClient;
  refreshAfterGroupChange: () => Promise<void>;
};

export const test = userSessionTest.extend<{
  flowDocumentSession: (user: DocumentUser) => Promise<DocumentSession>;
  flowRefreshDocumentSession: () => Promise<void>;
}>({
  flowRefreshDocumentSession: async ({ page, pageWorkspace, appUser }, use) => {
    await use(() => refreshOwnSessionAfterGroupChange(page, pageWorkspace, appUser));
  },
  flowDocumentSession: async ({ browser, browserContextOptions, apiContext }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (user) => {
        const { page, close } = await loginInNewContext(browser, browserContextOptions, user.username, user.password);
        closers.push(close);
        const workspace = new WorkspacePage(page);
        return {
          workspace,
          document: new DocumentPage(page),
          editor: new DocumentEditorPage(page),
          notebook: new NotebookPage(page),
          sharedDocuments: new SharedDocumentsPage(page),
          groupDetails: new GroupDetailsPage(page),
          directory: new DirectoryPage(page),
          documents: new DocumentsClient(apiContext, user.apiKey),
          folders: new FoldersClient(apiContext, user.apiKey),
          refreshAfterGroupChange: () => refreshOwnSessionAfterGroupChange(page, workspace, user),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },
});
