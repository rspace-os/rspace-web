import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: { search: { performSearch: vi.fn() }, getTemplate: vi.fn(() => Promise.resolve(null)) },
    uiStore: { addAlert: vi.fn() },
    unitStore: { getUnit: () => ({ label: "ml" }), unitsOfCategory: () => [] },
  }),
}));

vi.mock("@/hooks/api/useUiPreference", () => ({
  PREFERENCES: {
    INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
    INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
  },
  default: (_pref: symbol, opts: { defaultValue: unknown }) => [opts.defaultValue, vi.fn()],
  useRawUiPreferences: () => ({}),
  readUiPreference: (_uiPreferences: Record<string, unknown>, _pref: symbol, defaultValue: unknown) => defaultValue,
}));

vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

/**
 * The wizard renews its origins' edit locks as the user steps through it. Renewal is a POST that
 * recreates a five-minute lock, so it has to be ordered against the release the close performs
 * (RSDEV-1231).
 */
describe("OperationWizard lock renewal and close ordering", () => {
  it("does not close until an in-flight lock renewal has settled", async () => {
    // Next fires a renewal POST. Closing while it is still in flight lets the caller's DELETE win
    // the race, and the late POST then recreates a lock on a wizard that is already gone, leaving
    // the origin locked for five minutes with nothing left to release it.
    server.use(
      http.get("/api/inventory/v1/samples/validateNameForNewSample", () => HttpResponse.json({ valid: true })),
    );
    const origin = makeMockSubSample({});
    let finishRenewal: (status: "LOCKED_OK") => void = () => {};
    vi.spyOn(origin, "acquireEditLock").mockReturnValue(
      new Promise<"LOCKED_OK">((resolve) => {
        finishRenewal = resolve;
      }),
    );
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByRole("combobox", { name: /fields\.processName/i }), "dna");
    await user.click(screen.getByRole("button", { name: /actions\.next/i }));

    await user.click(screen.getByRole("button", { name: /actions\.cancel/i }));
    expect(onClose).not.toHaveBeenCalled();

    finishRenewal("LOCKED_OK");
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });
});
