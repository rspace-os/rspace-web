import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
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

describe("OperationWizard lock renewal and close ordering", () => {
  beforeEach(() => {
    // The dedup effect fires this once a process name exists; answer it so nothing is unhandled.
    server.use(
      http.get("/api/inventory/v1/samples/validateNameForNewSample", () => HttpResponse.json({ valid: true })),
    );
  });

  /**
   * Drives the wizard to the step after details, where the first lock renewal has been started.
   * `finish` collects one resolver per renewal, in the order the wizard started them.
   */
  async function wizardAtStepTwo() {
    const origin = makeMockSubSample({});
    const finish: Array<(status: "LOCKED_OK") => void> = [];
    vi.spyOn(origin, "acquireEditLock").mockImplementation(
      () =>
        new Promise<"LOCKED_OK">((resolve) => {
          finish.push(resolve);
        }),
    );
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByRole("combobox", { name: /fields\.processName/i }), "dna");
    await user.click(screen.getByRole("button", { name: /actions\.next/i }));
    return { finish, onClose, user };
  }

  it("does not close until an in-flight lock renewal has settled", async () => {
    // Next fires a renewal POST. Closing while it is still in flight lets the caller's DELETE win
    // the race, and the late POST then recreates a lock on a wizard that is already gone, leaving
    // the origin locked for five minutes with nothing left to release it.
    const { finish, onClose, user } = await wizardAtStepTwo();

    await user.click(screen.getByRole("button", { name: /actions\.cancel/i }));
    expect(onClose).not.toHaveBeenCalled();

    finish[0]("LOCKED_OK");
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("waits for every pending renewal, including one that settles after a later one", async () => {
    // Next starts renewal A and Back starts renewal B. Tracking only the most recent batch drops A
    // from the chain, so a close after B settles releases the locks while A's POST is still in
    // flight, and A then recreates a five-minute lock on a wizard that is gone.
    const { finish, onClose, user } = await wizardAtStepTwo();
    await user.click(screen.getByRole("button", { name: /actions\.back/i }));
    expect(finish).toHaveLength(2);

    finish[1]("LOCKED_OK");
    await user.click(screen.getByRole("button", { name: /actions\.cancel/i }));
    expect(onClose).not.toHaveBeenCalled();

    finish[0]("LOCKED_OK");
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("starts no further renewal once close has begun", async () => {
    // Close captures the renewals outstanding at that instant, so a batch started afterwards would
    // not be waited for and could land behind the caller's release. Nothing may start one: a wizard
    // that is closing has no lock left to keep alive.
    const { finish, onClose, user } = await wizardAtStepTwo();
    expect(finish).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: /actions\.cancel/i }));
    await user.click(screen.getByRole("button", { name: /actions\.back/i }));
    expect(finish).toHaveLength(1);

    finish[0]("LOCKED_OK");
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });
});
