import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useUiPreference";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor, within } from "@testing-library/react";
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

// USER_LISTING holds the first 10 of 16 users; ALL_USERS is what "export all" fetches
const ALL_USERS = {
  ...USER_LISTING,
  userInfo: {
    ...USER_LISTING.userInfo,
    hitsPerPage: 16,
    results: Array.from({ length: 16 }, (_, i) => {
      const u = USER_LISTING.userInfo.results[i % 10];
      return { ...u, userInfo: { ...u.userInfo, id: -100 - i, username: `user${i}` } };
    }),
  },
};

function mockRequests(fullListing: () => Promise<Response> | Response) {
  server.use(
    http.get("*/system/ajax/jsonList", ({ request }) =>
      new URL(request.url).searchParams.get("resultsPerPage") === "16"
        ? fullListing()
        : HttpResponse.json(USER_LISTING),
    ),
    http.get("/export/ajax/defaultPDFConfig", () => HttpResponse.json(PDF_CONFIG)),
    http.get("*/analyticsProperties", () => HttpResponse.json({ analyticsEnabled: false })),
    http.get("/vfpwd/ajax/checkVerificationPasswordNeeded", () => HttpResponse.json({ data: false })),
  );
}

function renderUsersPage() {
  render(
    <ThemeProvider theme={materialTheme}>
      <UsersPage />
    </ThemeProvider>,
  );
}

async function clickExportAllRows(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "Export" }));
  await user.click(await screen.findByRole("menuitem", { name: "system:usersPage.export.allRows" }));
}

describe("Export all rows", () => {
  test("While the listing is still loading, the Export button should be disabled.", async () => {
    mockRequests(() => HttpResponse.json(ALL_USERS));
    server.use(
      http.get("*/system/ajax/jsonList", async () => {
        await delay("infinite");
        return HttpResponse.json(USER_LISTING);
      }),
    );
    renderUsersPage();

    expect(await screen.findByRole("button", { name: "Export" })).toBeDisabled();
  });

  test("Every user of the full listing should be included, not just the current page.", async () => {
    let blob: Blob | undefined;
    const createObjectURL = vi.fn().mockImplementation((b: Blob) => {
      blob = b;
      return "";
    });
    window.URL.createObjectURL = createObjectURL;
    mockRequests(() => HttpResponse.json(ALL_USERS));
    const user = userEvent.setup();
    renderUsersPage();

    await clickExportAllRows(user);

    await waitFor(() => expect(createObjectURL).toHaveBeenCalled());
    if (typeof blob === "undefined") throw new Error("Impossible, because createObjectURL has been called");
    expect((await blob.text()).split("\n").length).toBe(17);
    await waitFor(() => expect(screen.getAllByRole("checkbox", { name: "Select row" })).toHaveLength(10));
  });

  test("While the full listing loads, the Export button should stay focusable but busy, and announce progress.", async () => {
    window.URL.createObjectURL = vi.fn().mockReturnValue("");
    let respond: () => void = () => {};
    const responded = new Promise<void>((resolve) => {
      respond = resolve;
    });
    mockRequests(async () => {
      await responded;
      return HttpResponse.json(ALL_USERS);
    });
    const user = userEvent.setup();
    renderUsersPage();

    await clickExportAllRows(user);

    expect(await screen.findByRole("status")).toHaveTextContent("system:usersPage.export.allRowsInProgress");
    const exportButton = screen.getByRole("button", { name: "Export" });
    expect(exportButton).toHaveAttribute("aria-disabled", "true");
    expect(exportButton).toBeEnabled();
    expect(within(exportButton).getByRole("progressbar", { hidden: true })).toBeVisible();
    expect(screen.getByRole("menuitem", { name: "system:usersPage.export.allRows" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    respond();
    await waitFor(() => expect(screen.getByRole("status")).toBeEmptyDOMElement());
    expect(screen.getByRole("button", { name: "Export" })).not.toHaveAttribute("aria-disabled");
  });

  test("If the full listing fails to load, an error should be shown and the previous page restored.", async () => {
    const restoreConsole = silenceConsole(["error"], ["Request failed"]);
    mockRequests(() => HttpResponse.json({ exceptionMessage: "Database is busy" }, { status: 500 }));
    const user = userEvent.setup();
    renderUsersPage();

    await clickExportAllRows(user);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("system:usersPage.export.allRowsFailed");
    expect(alert).toHaveTextContent("Database is busy");
    await waitFor(() => expect(screen.getAllByRole("checkbox", { name: "Select row" })).toHaveLength(10));
    expect(screen.getByRole("button", { name: "Export" })).toBeEnabled();
    restoreConsole();
  });
});
