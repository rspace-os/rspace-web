import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationDetailsStep from "../OperationDetailsStep";
import OperationWizard from "../OperationWizard";
import { operations } from "./testOperations";

/*
 * The store is mocked as a fresh profile - getUnit answering undefined for every id - which is the
 * one thing OperationWizard.test.tsx's category-aware mock cannot express. In that window
 * SubSampleModel.quantityCategory throws, and ProcessAction mounts this wizard as soon as the
 * selection is processable, NOT only while the dialog is open, so the throw took out the whole
 * context menu.
 */
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: {
      search: { performSearch: vi.fn(), fetcher: { permalink: null, performInitialSearch: vi.fn() } },
      getTemplate: vi.fn(() => Promise.resolve(null)),
    },
    uiStore: { addAlert: vi.fn() },
    unitStore: { getUnit: () => undefined, unitsOfCategory: () => [] },
  }),
}));

vi.mock("@/hooks/api/useUiPreference", () => ({
  default: (_pref: symbol, opts: { defaultValue: unknown }) => [opts.defaultValue, vi.fn()],
}));

vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

const poolOperation = (() => {
  const pool = operations.find((o) => o.key === "pool");
  if (!pool) throw new Error("the test config must declare pool");
  return pool;
})();

function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("OperationWizard while the unit store is still loading", () => {
  /*
   * Molarity (unit ids 11-14) and concentration (15-17) are real, server-supported inventory units
   * (RSUnitDef), and the sample/subsample API applies no category restriction, so a subsample can
   * genuinely hold one. The static unit table only knows volume, mass and dimensionless, so
   * toCommonUnit -> atomicUnitOfSameCategory THROWS "Unknown unit: N" for them.
   */
  it.each([
    ["a single origin", [makeMockSubSample({})]],
    ["a multi-origin (Pool) selection", [makeMockSubSample({}), makeMockSubSample({})]],
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

  it("renders the per-subsample amount inputs without dereferencing the unit store", () => {
    expect(() =>
      render(
        <OperationDetailsStep
          operation={poolOperation}
          origin={makeMockSubSample({})}
          origins={[makeMockSubSample({}), makeMockSubSample({})]}
          values={{}}
          onChange={vi.fn()}
          section="amounts"
          amountMode="perSubsample"
          perSubsampleAmounts={{}}
          onPerSubsampleAmountsChange={vi.fn()}
          onAmountModeChange={vi.fn()}
        />,
      ),
    ).not.toThrow();
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
