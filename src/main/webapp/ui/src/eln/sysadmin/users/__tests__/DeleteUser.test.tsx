import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useUiPreference";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, HttpResponse, http } from "msw";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import materialTheme from "../../../../theme";
import { UsersPage } from "..";
import PDF_CONFIG from "./pdfConfig.json";
import USER_LISTING from "./userListing.json";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));
vi.mock("@/modules/raid/queries", () => ({
  useRaidIntegrationInfoAjaxQuery: () => ({
    data: { success: true, data: { enabled: false } },
  }),
}));
vi.mock("@/modules/share/queries", () => ({
  useCommonGroupsShareListingQuery: () => ({ data: new Map() }),
}));

window.RS = { newFileStoresExportEnabled: false };

const USER8H_ID = "-5";

function mockRequests(formsAndTemplatesUsage: () => Response | Promise<Response>) {
  server.use(
    http.get("*/system/ajax/jsonList", () => HttpResponse.json(USER_LISTING)),
    http.get("/export/ajax/defaultPDFConfig", () => HttpResponse.json(PDF_CONFIG)),
    http.get("*/analyticsProperties", () => HttpResponse.json({ analyticsEnabled: false })),
    http.get("/deploymentproperties/ajax/property", () => HttpResponse.json(true)),
    http.get("/vfpwd/ajax/checkVerificationPasswordNeeded", () => HttpResponse.json({ data: false })),
    http.get("/system/ajax/removeUserAccount/formsAndTemplatesUsage", ({ request }) =>
      new URL(request.url).searchParams.get("userId") === USER8H_ID
        ? formsAndTemplatesUsage()
        : new HttpResponse(null, { status: 404 }),
    ),
  );
}

async function openDeleteDialogForUser8h() {
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ThemeProvider theme={materialTheme}>
        <UsersPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
  await user.click(within(await screen.findByRole("row", { name: /user8h/ })).getByRole("checkbox"));
  await user.click(screen.getByRole("button", { name: "system:usersPage.actionsLabel" }));
  await user.click(await screen.findByRole("menuitem", { name: /common:actions.delete/ }));
  return within(await screen.findByRole("dialog"));
}

describe("Delete user", () => {
  test("When the user's forms or templates are used by others, the dialog should say they will be transferred.", async () => {
    mockRequests(() => HttpResponse.json({ formsUsedByOtherUsers: true, templatesUsedByOtherUsers: false }));

    const dialog = await openDeleteDialogForUser8h();

    expect(
      await dialog.findByRole("button", { name: "system:usersPage.deleteDialog.transferAndDelete" }),
    ).toBeEnabled();
    expect(dialog.getByText("system:usersPage.deleteDialog.formsAndTemplatesNotice")).toBeVisible();
    expect(dialog.getByText("system:usersPage.deleteDialog.formsNotice")).toBeVisible();
  });

  test("When only the user's templates are used by others, the dialog should not mention forms.", async () => {
    mockRequests(() => HttpResponse.json({ formsUsedByOtherUsers: false, templatesUsedByOtherUsers: true }));

    const dialog = await openDeleteDialogForUser8h();

    expect(
      await dialog.findByRole("button", { name: "system:usersPage.deleteDialog.transferAndDelete" }),
    ).toBeEnabled();
    expect(dialog.getByText("system:usersPage.deleteDialog.formsAndTemplatesNotice")).toBeVisible();
    expect(dialog.queryByText("system:usersPage.deleteDialog.formsNotice")).not.toBeInTheDocument();
  });

  test("While the transfer check loads, deleting should not be possible.", async () => {
    mockRequests(async () => {
      await delay("infinite");
      return HttpResponse.json({ formsUsedByOtherUsers: false, templatesUsedByOtherUsers: false });
    });

    const dialog = await openDeleteDialogForUser8h();

    expect(dialog.getByRole("button", { name: /common:actions.delete/ })).toBeDisabled();
  });

  test("When nothing would be transferred, the dialog should offer a plain delete.", async () => {
    mockRequests(() => HttpResponse.json({ formsUsedByOtherUsers: false, templatesUsedByOtherUsers: false }));

    const dialog = await openDeleteDialogForUser8h();

    expect(await dialog.findByRole("button", { name: "common:actions.delete" })).toBeEnabled();
    expect(dialog.queryByRole("alert")).not.toBeInTheDocument();
  });

  test("When the transfer check fails, the dialog should warn and still allow deletion.", async () => {
    const restoreConsole = silenceConsole(["error"], ["Request failed"]);
    mockRequests(() => new HttpResponse(null, { status: 500 }));

    const dialog = await openDeleteDialogForUser8h();

    expect(await dialog.findByText("system:usersPage.deleteDialog.formsAndTemplatesCheckFailed")).toBeVisible();
    expect(dialog.getByRole("button", { name: "system:usersPage.deleteDialog.transferAndDelete" })).toBeEnabled();
    expect(dialog.queryByText("system:usersPage.deleteDialog.formsNotice")).not.toBeInTheDocument();
    restoreConsole();
  });
});
