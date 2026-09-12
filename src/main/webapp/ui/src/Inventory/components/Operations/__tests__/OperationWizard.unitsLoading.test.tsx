import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationDetailsStep from "../OperationDetailsStep";
import OperationWizard from "../OperationWizard";
import { parseOperationsConfig } from "../operationsConfig";
import { rawConfig } from "./testOperations";

/*
 * The unit store starts empty on a fresh profile: SAVED_UNITS reads localStorage, which has no
 * "units" entry until GET /units has resolved once, so UnitStore.getUnit returns undefined for
 * every id in that window. SubSampleModel.quantityCategory throws "Could not get unit category"
 * when it does.
 *
 * ProcessAction mounts this wizard whenever the selection is processable, NOT only while the dialog
 * is open, so the wizard's body runs as soon as a context menu opens. A throw there took out the
 * whole menu before the operation picker had appeared (Copilot review, PR #1090).
 *
 * The store is therefore mocked as a fresh profile here - getUnit answering undefined for every id
 * - which is the one thing OperationWizard.test.tsx's category-aware mock cannot express.
 */
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: { search: { performSearch: vi.fn() }, getTemplate: vi.fn(() => Promise.resolve(null)) },
    uiStore: { addAlert: vi.fn() },
    // A real UnitStore in this state: seeded from an empty localStorage, so getUnit misses for
    // every id and every category is empty until GET /units resolves.
    unitStore: { getUnit: () => undefined, unitsOfCategory: () => [] },
  }),
}));

vi.mock("@/hooks/api/useUiPreference", () => ({
  PREFERENCES: {
    INVENTORY_OPERATION_PROCESS_VALUES: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES"),
    INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
    INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
  },
  default: (_pref: symbol, opts: { defaultValue: unknown }) => [opts.defaultValue, vi.fn()],
}));

vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

/** The Pool definition from the real config, the one operation with a per-subsample amount mode. */
const poolOperation = (() => {
  const pool = parseOperationsConfig(rawConfig).find((o) => o.key === "pool");
  if (!pool) throw new Error("the test config must declare pool");
  return pool;
})();

function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  server.use(http.get("/api/inventory/v1/operations/config", () => HttpResponse.json(rawConfig)));
});

describe("OperationWizard while the unit store is still loading", () => {
  it("mounts for a closed dialog without dereferencing the unit store", () => {
    expect(() =>
      render(<OperationWizard open={false} onClose={vi.fn()} origins={[makeMockSubSample({})]} />),
    ).not.toThrow();
  });

  it("renders the per-subsample amount inputs without dereferencing the unit store", () => {
    // Pool -> Per subsample renders one amount input per origin, each with its own unit select.
    // That select asked the origin for its category through the throwing getter, so the amounts
    // step crashed while /units was still in flight (Copilot review, PR #1090).
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

  it("mounts for a multi-origin (Pool) selection without dereferencing the unit store", () => {
    expect(() =>
      render(
        <OperationWizard open={false} onClose={vi.fn()} origins={[makeMockSubSample({}), makeMockSubSample({})]} />,
      ),
    ).not.toThrow();
  });
});

describe("OperationWizard for an origin whose unit has no atomic unit", () => {
  /*
   * Molarity (unit ids 11-14) and concentration (15-17) are real, server-supported inventory units
   * (RSUnitDef), and the sample/subsample API applies no category restriction, so a subsample can
   * genuinely hold one. The static unit table only knows volume, mass and dimensionless, so
   * toCommonUnit -> atomicUnitOfSameCategory THROWS "Unknown unit: N" for them.
   *
   * That is the same shape as the quantityCategory throw above, and it reaches the same place: the
   * wizard picks its representative origin during render, and ProcessAction mounts the wizard as
   * soon as the selection is processable, so the throw takes out the whole context menu.
   */
  it("mounts for a molarity origin without throwing", () => {
    expect(() =>
      render(
        <OperationWizard
          open={false}
          onClose={vi.fn()}
          origins={[makeMockSubSample({ quantity: { numericValue: 1, unitId: 11 } })]}
        />,
      ),
    ).not.toThrow();
  });

  it("mounts for a multi-origin selection holding concentration quantities without throwing", () => {
    expect(() =>
      render(
        <OperationWizard
          open={false}
          onClose={vi.fn()}
          origins={[
            makeMockSubSample({ id: 1, globalId: "SS1", quantity: { numericValue: 2, unitId: 15 } }),
            makeMockSubSample({ id: 2, globalId: "SS2", quantity: { numericValue: 1, unitId: 15 } }),
          ]}
        />,
      ),
    ).not.toThrow();
  });
});
