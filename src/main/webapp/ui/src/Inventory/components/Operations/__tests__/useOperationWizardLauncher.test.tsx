import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Activity } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import type { OperationResult } from "../operationsApi";
import { useOperationWizardLauncher } from "../useOperationWizardLauncher";
import { fakeServerLocks } from "./fakeServerLocks";

const created: OperationResult = { id: 9, globalId: "SA9", name: "New" };
vi.mock("../OperationWizard", () => ({
  default: ({
    open,
    onClose,
    onPerformed,
    origins,
    pendingRenewals,
  }: {
    open: boolean;
    onClose: () => void;
    onPerformed?: (sample: OperationResult | null) => void;
    origins: Array<SubSampleModel>;
    pendingRenewals?: { current: Promise<unknown> };
  }) =>
    open ? (
      <div data-testid="wizard">
        <button type="button" aria-label="perform" onClick={() => onPerformed?.(created)} />
        <button
          type="button"
          aria-label="next step"
          onClick={() => {
            if (pendingRenewals) pendingRenewals.current = Promise.all(origins.map((o) => o.acquireEditLock()));
          }}
        />
        <button type="button" aria-label="close wizard" onClick={onClose} />
      </div>
    ) : null,
}));
const gate = { available: true };
vi.mock("../../ContextMenu/useProcessAvailable", () => ({ useProcessAvailable: () => gate.available }));
const addAlert = vi.fn();
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) }, uiStore: { addAlert } }),
}));

function Workflow({
  origin,
  onPerformed,
  onClose,
  onLaunched,
}: {
  origin: SubSampleModel;
  onPerformed?: (sample: OperationResult | null) => void;
  onClose?: (performed: boolean) => void;
  onLaunched: (opened: boolean) => void;
}) {
  const { launch, wizard } = useOperationWizardLauncher([origin], { onPerformed, onClose });
  return (
    <>
      <button type="button" aria-label="launch" onClick={() => void launch().then(onLaunched)} />
      {wizard}
    </>
  );
}

function lockedOrigin(overrides: Parameters<typeof makeMockSubSample>[0] = {}) {
  const origin = makeMockSubSample(overrides);
  const acquire = vi.spyOn(origin, "acquireEditLock").mockResolvedValue("LOCKED_OK");
  const release = vi.spyOn(origin, "releaseLock").mockResolvedValue(null);
  return { origin, acquire, release };
}

beforeEach(() => {
  gate.available = true;
  addAlert.mockClear();
});

describe("useOperationWizardLauncher", () => {
  it("hands the created sample over, then reports a performed close once the lock is released", async () => {
    const user = userEvent.setup();
    const { origin, release } = lockedOrigin();
    const onPerformed = vi.fn();
    const onClose = vi.fn();
    const onLaunched = vi.fn();
    render(<Workflow origin={origin} onPerformed={onPerformed} onClose={onClose} onLaunched={onLaunched} />);

    await user.click(screen.getByRole("button", { name: "launch" }));
    await waitFor(() => expect(onLaunched).toHaveBeenCalledWith(true));
    await user.click(screen.getByRole("button", { name: "perform" }));
    await user.click(screen.getByRole("button", { name: "close wizard" }));

    expect(onPerformed).toHaveBeenCalledWith(created);
    await waitFor(() => expect(onClose).toHaveBeenCalledWith(true));
    expect(release).toHaveBeenCalled();
    expect(screen.queryByTestId("wizard")).not.toBeInTheDocument();
  });

  it("reports a cancelled close when the wizard closes without performing, and resets for the next launch", async () => {
    const user = userEvent.setup();
    const { origin } = lockedOrigin();
    const onClose = vi.fn();
    render(<Workflow origin={origin} onClose={onClose} onLaunched={() => {}} />);

    await user.click(screen.getByRole("button", { name: "launch" }));
    await user.click(await screen.findByRole("button", { name: "perform" }));
    await user.click(screen.getByRole("button", { name: "close wizard" }));
    await waitFor(() => expect(onClose).toHaveBeenLastCalledWith(true));

    await user.click(screen.getByRole("button", { name: "launch" }));
    await user.click(await screen.findByRole("button", { name: "close wizard" }));
    await waitFor(() => expect(onClose).toHaveBeenLastCalledWith(false));
  });

  it("still frees the lock at close, and opens again, after its effects were torn down and re-run mid-session", async () => {
    // Hot reload, StrictMode and a hidden <Activity> all run the cleanup and then the setup again
    // without unmounting. The cleanup gives the locks back, the next step's renewal takes them again,
    // and close must still release them or the next launch is refused as WAS_ALREADY_LOCKED.
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    const serverLocks = fakeServerLocks(origin);
    const onLaunched = vi.fn();
    const ui = (mode: "visible" | "hidden") => (
      <Activity mode={mode}>
        <Workflow origin={origin} onLaunched={onLaunched} />
      </Activity>
    );
    const { rerender } = render(ui("visible"));

    await user.click(screen.getByRole("button", { name: "launch" }));
    await waitFor(() => expect(onLaunched).toHaveBeenLastCalledWith(true));
    rerender(ui("hidden"));
    rerender(ui("visible"));
    await user.click(await screen.findByRole("button", { name: "next step" }));
    await user.click(screen.getByRole("button", { name: "close wizard" }));
    await waitFor(() => expect(serverLocks.size).toBe(0));

    await user.click(screen.getByRole("button", { name: "launch" }));
    await waitFor(() => expect(onLaunched).toHaveBeenCalledTimes(2));
    expect(onLaunched).toHaveBeenLastCalledWith(true);
    expect(screen.getByTestId("wizard")).toBeInTheDocument();
  });

  it("resolves false without opening or reporting a close when the lock is refused", async () => {
    const user = userEvent.setup();
    const { origin, acquire } = lockedOrigin();
    acquire.mockResolvedValue("WAS_ALREADY_LOCKED");
    const onClose = vi.fn();
    const onLaunched = vi.fn();
    render(<Workflow origin={origin} onClose={onClose} onLaunched={onLaunched} />);

    await user.click(screen.getByRole("button", { name: "launch" }));

    await waitFor(() => expect(onLaunched).toHaveBeenCalledWith(false));
    expect(screen.queryByTestId("wizard")).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("refuses with an info alert, taking no lock, when operations are not enabled", async () => {
    const user = userEvent.setup();
    gate.available = false;
    const { origin, acquire } = lockedOrigin();
    const onLaunched = vi.fn();
    render(<Workflow origin={origin} onLaunched={onLaunched} />);

    await user.click(screen.getByRole("button", { name: "launch" }));

    await waitFor(() => expect(onLaunched).toHaveBeenCalledWith(false));
    expect(acquire).not.toHaveBeenCalled();
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "notice" }));
  });

  it("refuses silently, taking no lock, for a deleted subsample", async () => {
    const user = userEvent.setup();
    const { origin, acquire } = lockedOrigin({ deleted: true });
    const onLaunched = vi.fn();
    render(<Workflow origin={origin} onLaunched={onLaunched} />);

    await user.click(screen.getByRole("button", { name: "launch" }));

    await waitFor(() => expect(onLaunched).toHaveBeenCalledWith(false));
    expect(acquire).not.toHaveBeenCalled();
    expect(addAlert).not.toHaveBeenCalled();
  });
});
