import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";
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
    unitStore: { getUnit: () => undefined },
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

  it("mounts for a multi-origin (Pool) selection without dereferencing the unit store", () => {
    expect(() =>
      render(
        <OperationWizard open={false} onClose={vi.fn()} origins={[makeMockSubSample({}), makeMockSubSample({})]} />,
      ),
    ).not.toThrow();
  });
});
