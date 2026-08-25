import MenuList from "@mui/material/MenuList";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { server } from "@/__tests__/mswServer";
import allIntegrationsAreDisabled from "@/eln/apps/__tests__/allIntegrationsAreDisabled.json";
import DmpImportDialogs, { DmpImportMenuSection, type DmpImportTarget } from "./DmpImportDialogs";

vi.mock("./Argos/DMPDialog", () => ({
  default: ({ open, onImport }: { open: boolean; onImport?: () => void }) =>
    open ? (
      <button type="button" onClick={onImport}>
        {"Argos dialog"}
      </button>
    ) : null,
}));

vi.mock("./DMPAssistant/DMPDialog", () => ({ default: () => null }));
vi.mock("./DMPOnline/DMPDialog", () => ({ default: () => null }));
vi.mock("./DMPTool/DMPDialog", () => ({ default: () => null }));
vi.mock("./DSW/DSWImportDialog", () => ({
  default: ({ open, connection }: { open: boolean; connection: { DSW_ALIAS: string } }) =>
    open ? <div>{connection.DSW_ALIAS}</div> : null,
}));

function renderMenu(data: typeof allIntegrationsAreDisabled.data): QueryClient {
  server.use(
    http.get("/integration/allIntegrations", () =>
      HttpResponse.json({
        ...allIntegrationsAreDisabled,
        data,
      }),
    ),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MenuList>
        <DmpImportMenuSection onSelect={() => {}} />
      </MenuList>
    </QueryClientProvider>,
  );
  return queryClient;
}

describe("DmpImportMenuSection", () => {
  test("shows enabled integrations and omits disabled integrations", async () => {
    renderMenu({
      ...allIntegrationsAreDisabled.data,
      ARGOS: { ...allIntegrationsAreDisabled.data.ARGOS, available: true, enabled: true },
    });

    expect(await screen.findByRole("menuitem", { name: "apps:dmpIntegrations.argos" })).toBeVisible();
    expect(screen.queryByRole("menuitem", { name: "apps:dmpIntegrations.dmptool" })).not.toBeInTheDocument();
  });

  test("renders nothing when every DMP integration is disabled", async () => {
    const queryClient = renderMenu(allIntegrationsAreDisabled.data);

    await waitFor(() => expect(queryClient.getQueryState(["integration", "allIntegrations"])?.status).toBe("success"));
    expect(screen.queryByText("gallery:sidebar.dmpImport")).not.toBeInTheDocument();
  });
});

describe("DmpImportDialogs", () => {
  test("passes the import callback to Argos", async () => {
    const user = userEvent.setup();
    const onImport = vi.fn();

    render(<DmpImportDialogs target={{ source: "argos" }} onClose={() => {}} onImport={onImport} />);
    await user.click(screen.getByRole("button", { name: "Argos dialog" }));

    expect(onImport).toHaveBeenCalledOnce();
  });

  test("passes the selected connection to DSW", () => {
    const target: DmpImportTarget = {
      source: "dsw",
      connection: { DSW_APIKEY: "key", DSW_URL: "https://dsw.example.com", DSW_ALIAS: "My DSW" },
    };

    render(<DmpImportDialogs target={target} onClose={() => {}} onImport={() => {}} />);

    expect(screen.getByText("My DSW")).toBeVisible();
  });
});
