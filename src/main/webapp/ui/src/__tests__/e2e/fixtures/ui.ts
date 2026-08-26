import type { BrowserContext, BrowserContextOptions, Page, TestFixture } from "@playwright/test";
import { test as base } from "@playwright/test";
import { GitHubAppsCardComponent } from "@/modules/github/__tests__/pageObjects/GitHubAppsCardComponent";
import { MsTeamsShareDialogComponent } from "@/modules/msteams/__tests__/pageObjects/MsTeamsShareDialogComponent";
import { OrcidProfilePage } from "@/modules/orcid/__tests__/pageObjects/OrcidProfilePage";
import { SlackDialogComponent } from "@/modules/slack/__tests__/pageObjects/SlackDialogComponent";
import { SlackShareDialogComponent } from "@/modules/slack/__tests__/pageObjects/SlackShareDialogComponent";
import { storageStatePath } from "../authState";
import { ExportWizardComponent } from "../components/shared/ExportWizardComponent";
import { NotificationsDialogComponent } from "../components/shared/NotificationsDialogComponent";
import { ToastsComponent } from "../components/shared/ToastsComponent";
import { env } from "../env";
import { AppsPage } from "../pageObjects/apps/AppsPage";
import { LoginPage } from "../pageObjects/auth/LoginPage";
import { RequestPasswordResetPage } from "../pageObjects/auth/RequestPasswordResetPage";
import { RequestUsernameReminderPage } from "../pageObjects/auth/RequestUsernameReminderPage";
import { ResetPasswordPage } from "../pageObjects/auth/ResetPasswordPage";
import { SignupPage } from "../pageObjects/auth/SignupPage";
import { DocumentEditorPage } from "../pageObjects/document/DocumentEditorPage";
import { DocumentPage } from "../pageObjects/document/DocumentPage";
import { GalleryPage } from "../pageObjects/gallery/GalleryPage";
import { IdentifiersPage } from "../pageObjects/inventory/IdentifiersPage";
import { InventoryImportPage } from "../pageObjects/inventory/InventoryImportPage";
import { InventoryPage } from "../pageObjects/inventory/InventoryPage";
import { AuditTrailPage } from "../pageObjects/myrspace/AuditTrailPage";
import { DeletedItemsPage } from "../pageObjects/myrspace/DeletedItemsPage";
import { MyRSpacePage } from "../pageObjects/myrspace/MyRSpacePage";
import { NotebookPage } from "../pageObjects/notebook/NotebookPage";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { type AppUser, USERS } from "../users";

export type E2EOptions = { appUser: AppUser };

type UiFixtures = {
  browserContextOptions: BrowserContextOptions;
  pageLogin: LoginPage;
  pageRequestPasswordReset: RequestPasswordResetPage;
  pageResetPassword: ResetPasswordPage;
  pageRequestUsernameReminder: RequestUsernameReminderPage;
  pageSignup: SignupPage;
  pageApps: AppsPage;
  pageWorkspace: WorkspacePage;
  pageDocument: DocumentPage;
  pageDocumentEditor: DocumentEditorPage;
  pageGallery: GalleryPage;
  pageInventory: InventoryPage;
  pageInventoryForUser: (user: AppUser) => Promise<InventoryPage>;
  pageInventoryImport: InventoryImportPage;
  pageIdentifiers: IdentifiersPage;
  pageMyRSpace: MyRSpacePage;
  pageAuditTrail: AuditTrailPage;
  pageDeletedItems: DeletedItemsPage;
  pageNotebook: NotebookPage;
  pageGitHubAppsCard: GitHubAppsCardComponent;
  pageOrcidProfile: OrcidProfilePage;
  componentExportWizard: ExportWizardComponent;
  componentNotifications: NotificationsDialogComponent;
  componentToasts: ToastsComponent;
  componentMsTeamsShare: MsTeamsShareDialogComponent;
  componentSlackDialog: SlackDialogComponent;
  componentSlackShare: SlackShareDialogComponent;
};

function pageFixture<T>(Ctor: new (page: Page) => T): TestFixture<T, { page: Page }> {
  return async ({ page }, use) => {
    await use(new Ctor(page));
  };
}

export const uiTest = base.extend<E2EOptions & UiFixtures>({
  appUser: [USERS.user1a, { option: true }],

  browserContextOptions: async ({ browserName }, use) => {
    await use({ baseURL: env.baseURL, ignoreHTTPSErrors: browserName === "webkit" });
  },
  pageLogin: pageFixture(LoginPage),
  pageRequestPasswordReset: pageFixture(RequestPasswordResetPage),
  pageResetPassword: pageFixture(ResetPasswordPage),
  pageRequestUsernameReminder: pageFixture(RequestUsernameReminderPage),
  pageSignup: pageFixture(SignupPage),
  pageApps: pageFixture(AppsPage),
  pageWorkspace: pageFixture(WorkspacePage),
  pageDocument: pageFixture(DocumentPage),
  pageDocumentEditor: pageFixture(DocumentEditorPage),
  pageGallery: pageFixture(GalleryPage),
  pageInventory: pageFixture(InventoryPage),
  pageInventoryForUser: async ({ browser, browserContextOptions }, use) => {
    const contexts: BrowserContext[] = [];
    try {
      await use(async (user) => {
        const context = await browser.newContext({
          ...browserContextOptions,
          storageState: storageStatePath(user.username),
        });
        contexts.push(context);
        return new InventoryPage(await context.newPage());
      });
    } finally {
      await Promise.all(contexts.map((context) => context.close()));
    }
  },
  pageInventoryImport: pageFixture(InventoryImportPage),
  pageIdentifiers: pageFixture(IdentifiersPage),
  pageMyRSpace: pageFixture(MyRSpacePage),
  pageAuditTrail: pageFixture(AuditTrailPage),
  pageDeletedItems: pageFixture(DeletedItemsPage),
  pageNotebook: pageFixture(NotebookPage),
  pageGitHubAppsCard: pageFixture(GitHubAppsCardComponent),
  pageOrcidProfile: pageFixture(OrcidProfilePage),
  componentExportWizard: pageFixture(ExportWizardComponent),
  componentNotifications: pageFixture(NotificationsDialogComponent),
  componentToasts: pageFixture(ToastsComponent),
  componentMsTeamsShare: pageFixture(MsTeamsShareDialogComponent),
  componentSlackDialog: pageFixture(SlackDialogComponent),
  componentSlackShare: pageFixture(SlackShareDialogComponent),
});
