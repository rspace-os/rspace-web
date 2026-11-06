import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import type React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import { server } from "@/__tests__/mswServer";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

let InEnglish: React.ComponentType<{ children: React.ReactNode }>;
beforeAll(async () => {
  InEnglish = await createRealI18nWrapper({ resources: { common, inventory }, defaultNS: "common" });
});

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: {
      search: { performSearch: vi.fn(), fetcher: { permalink: null, performInitialSearch: vi.fn() } },
      getTemplate: vi.fn(() => Promise.resolve(null)),
    },
    uiStore: { addAlert: vi.fn() },
  }),
}));

vi.mock("@/hooks/api/useUiPreference", () => ({
  default: (_pref: symbol, opts: { defaultValue: unknown }) => [opts.defaultValue, vi.fn()],
}));

vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("OperationWizard for unsupported amount categories", () => {
  // Closed wizards mount in the context menu, so unsupported units must not crash it.
  it.each([
    ["a molarity origin", [makeMockSubSample({ quantity: { numericValue: 1, unitId: 11 } })]],
    [
      "multiple origins holding concentration quantities",
      [
        makeMockSubSample({ id: 1, globalId: "SS1", quantity: { numericValue: 2, unitId: 15 } }),
        makeMockSubSample({ id: 2, globalId: "SS2", quantity: { numericValue: 1, unitId: 15 } }),
      ],
    ],
  ])("mounts a closed dialog for %s without throwing", (_name, origins) => {
    expect(() => render(<OperationWizard open={false} onClose={vi.fn()} origins={origins} />)).not.toThrow();
  });
});

describe("OperationWizard for an origin whose unit has no atomic unit", () => {
  it("explains, on the details step, why Next is disabled for a molarity origin", async () => {
    // The dedup effect fires this once a process name exists; answer it so nothing is unhandled.
    server.use(
      http.get("/api/inventory/v1/samples/validateNameForNewSample", () => HttpResponse.json({ valid: true })),
    );
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({ quantity: { numericValue: 1, unitId: 11 } })]}
      />,
    );
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    // A process name alone would enable Next for a millilitre origin, so the only thing holding
    // it here is the origin's unit.
    await user.type(screen.getByRole("combobox", { name: /fields\.processName/i }), "dna");
    expect(screen.getByRole("button", { name: /actions\.next/i })).toBeDisabled();
    expect(screen.getByRole("alert")).not.toBeEmptyDOMElement();
  });
});

describe("OperationWizard's details step for a Pool", () => {
  it("names every empty origin, not just the representative one", async () => {
    const user = userEvent.setup();
    const empty = (id: number, name: string) =>
      makeMockSubSample({ id, globalId: `SS${id}`, name, quantity: { numericValue: 0, unitId: 3 } });
    render(
      <InEnglish>
        <OperationWizard
          open
          onClose={vi.fn()}
          origins={[empty(1, "Vial A"), empty(2, "Vial B"), makeMockSubSample({ id: 3, globalId: "SS3" })]}
        />
      </InEnglish>,
    );
    await user.click(await screen.findByRole("button", { name: /^Pool/ }));
    expect(screen.getByText("Subsamples Vial A and Vial B have an amount of 0.")).toBeInTheDocument();
  });
});
