import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { RecordLockedError } from "@/stores/models/InventoryBaseRecord";
import materialTheme from "@/theme";
import { lockOwnerName } from "../lockAlerts";
import ProcessAction, { isProcessableSelection } from "../ProcessAction";

let settleRenewal: () => void = () => {};
vi.mock("../../Operations/OperationWizard", () => ({
  default: ({
    open,
    onClose,
    origins,
    pendingRenewals,
  }: {
    open: boolean;
    onClose: () => void;
    origins: Array<{ globalId: string }>;
    pendingRenewals?: { current: Promise<unknown> };
  }) =>
    open ? (
      <div data-testid="wizard" data-origins={origins.map((o) => o.globalId).join(",")}>
        <button type="button" onClick={onClose} aria-label="close wizard" />
        <button
          type="button"
          aria-label="start renewal"
          onClick={() => {
            if (!pendingRenewals) return;
            pendingRenewals.current = new Promise<void>((resolve) => {
              settleRenewal = resolve;
            });
          }}
        />
      </div>
    ) : null,
}));
const addAlert = vi.fn();
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) }, uiStore: { addAlert } }),
}));

describe("isProcessableSelection", () => {
  it("requires at least one record, all of them subsamples", () => {
    expect(isProcessableSelection([])).toBe(false);
    expect(isProcessableSelection([makeMockSubSample({})])).toBe(true);
    expect(isProcessableSelection([makeMockSubSample({}), makeMockSubSample({ id: 2, globalId: "SS2" })])).toBe(true);
    expect(isProcessableSelection([makeMockSubSample({}), {} as InventoryRecord])).toBe(false);
  });
});

describe("ProcessAction", () => {
  it("uses the same icon as the Derive operation (code-branch), tying the menu entry to the wizard", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ProcessAction as="button" disabled="" selectedResults={[makeMockSubSample({})]} closeMenu={() => {}} />
      </ThemeProvider>,
    );
    const button = screen.getByRole("button", { name: /operations\.action\.process/i });
    expect(button.querySelector('svg[data-icon="code-branch"]')).toBeInTheDocument();
  });
});

const holder = { firstName: "Carol", lastName: "Holder", username: "carol" };

function renderAction(records: Array<InventoryRecord>) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ProcessAction as="button" disabled="" selectedResults={records} closeMenu={() => {}} />
    </ThemeProvider>,
  );
}

describe("ProcessAction lock acquisition", () => {
  beforeEach(() => {
    addAlert.mockClear();
  });

  it("locks every origin before the wizard opens", async () => {
    const one = makeMockSubSample({});
    const two = makeMockSubSample({ id: 2, globalId: "SS2" });
    const acquireOne = vi.spyOn(one, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    const acquireTwo = vi.spyOn(two, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    renderAction([one, two]);

    await userEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));

    await waitFor(() => expect(screen.getByTestId("wizard")).toBeInTheDocument());
    expect(acquireOne).toHaveBeenCalledTimes(1);
    expect(acquireTwo).toHaveBeenCalledTimes(1);
  });

  it("gives back only the locks it took when acquisition partly fails", async () => {
    const fresh = makeMockSubSample({});
    const alreadyMine = makeMockSubSample({ id: 2, globalId: "SS2" });
    const held = makeMockSubSample({ id: 3, globalId: "SS3" });
    vi.spyOn(fresh, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    vi.spyOn(alreadyMine, "acquireEditLock").mockResolvedValue("WAS_ALREADY_LOCKED");
    vi.spyOn(held, "acquireEditLock").mockRejectedValue(new RecordLockedError(held, holder));
    const releaseFresh = vi.spyOn(fresh, "releaseLock").mockResolvedValue(true);
    const releaseAlreadyMine = vi.spyOn(alreadyMine, "releaseLock").mockResolvedValue(true);
    renderAction([fresh, alreadyMine, held]);

    await userEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(releaseFresh).toHaveBeenCalled();
    expect(releaseAlreadyMine).not.toHaveBeenCalled();
    expect(screen.queryByTestId("wizard")).not.toBeInTheDocument();
    const details = (addAlert.mock.calls[0][0] as { details: Array<{ record: unknown }> }).details;
    expect(details).toHaveLength(1);
    expect(details[0].record).toBe(held);
    expect(lockOwnerName(holder)).toBe("Carol Holder");
  });

  it("ignores a second click while the first acquisition is still in flight", async () => {
    const origin = makeMockSubSample({});
    let finishSecond: (status: "WAS_ALREADY_LOCKED") => void = () => {};
    const secondAttempt = new Promise<"WAS_ALREADY_LOCKED">((resolve) => {
      finishSecond = resolve;
    });
    const acquire = vi
      .spyOn(origin, "acquireEditLock")
      .mockResolvedValueOnce("LOCKED_OK")
      .mockReturnValueOnce(secondAttempt);
    const release = vi.spyOn(origin, "releaseLock").mockResolvedValue(true);
    renderAction([origin]);

    const button = screen.getByRole("button", { name: /operations\.action\.process/i });
    fireEvent.click(button);
    fireEvent.click(button);
    finishSecond("WAS_ALREADY_LOCKED");

    await waitFor(() => expect(screen.getByTestId("wizard")).toBeInTheDocument());
    expect(acquire).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole("button", { name: /close wizard/i }));
    await waitFor(() => expect(release).toHaveBeenCalled());
  });
  it("refuses to open when an origin is already locked by this user elsewhere", async () => {
    // WAS_ALREADY_LOCKED identifies the holder only by username, so it may be this user's own
    // edit form in another tab, which can still save its stale quantity over what the wizard
    // commits.
    const fresh = makeMockSubSample({});
    const elsewhere = makeMockSubSample({ id: 2, globalId: "SS2" });
    vi.spyOn(fresh, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    vi.spyOn(elsewhere, "acquireEditLock").mockResolvedValue("WAS_ALREADY_LOCKED");
    const releaseFresh = vi.spyOn(fresh, "releaseLock").mockResolvedValue(true);
    const releaseElsewhere = vi.spyOn(elsewhere, "releaseLock").mockResolvedValue(true);
    renderAction([fresh, elsewhere]);

    await userEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(screen.queryByTestId("wizard")).not.toBeInTheDocument();
    expect(releaseFresh).toHaveBeenCalled();
    expect(releaseElsewhere).not.toHaveBeenCalled();
  });
  it("releases a lock that arrives after the action has unmounted", async () => {
    const origin = makeMockSubSample({});
    let grant: (status: "LOCKED_OK") => void = () => {};
    vi.spyOn(origin, "acquireEditLock").mockReturnValue(
      new Promise<"LOCKED_OK">((resolve) => {
        grant = resolve;
      }),
    );
    const release = vi.spyOn(origin, "releaseLock").mockResolvedValue(true);
    const { unmount } = renderAction([origin]);

    fireEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));
    unmount();
    grant("LOCKED_OK");

    await waitFor(() => expect(release).toHaveBeenCalled());
  });
  it("opens on the origins it locked, not on a selection that changed while it was acquiring", async () => {
    const locked = makeMockSubSample({});
    const swappedIn = makeMockSubSample({ id: 2, globalId: "SS2" });
    let grant: (status: "LOCKED_OK") => void = () => {};
    vi.spyOn(locked, "acquireEditLock").mockReturnValue(
      new Promise<"LOCKED_OK">((resolve) => {
        grant = resolve;
      }),
    );
    const acquireSwappedIn = vi.spyOn(swappedIn, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    const { rerender } = renderAction([locked]);

    fireEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));
    rerender(
      <ThemeProvider theme={materialTheme}>
        <ProcessAction as="button" disabled="" selectedResults={[swappedIn]} closeMenu={() => {}} />
      </ThemeProvider>,
    );
    grant("LOCKED_OK");

    await waitFor(() => expect(screen.getByTestId("wizard")).toBeInTheDocument());
    expect(screen.getByTestId("wizard")).toHaveAttribute("data-origins", "SS1");
    expect(acquireSwappedIn).not.toHaveBeenCalled();
  });
  it("waits for an in-flight renewal before releasing when the action unmounts", async () => {
    // Renewal is a POST that recreates the lock: releasing while one is in flight lets the DELETE
    // go first, and the late POST then re-locks an origin whose wizard is gone.
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    const release = vi.spyOn(origin, "releaseLock").mockResolvedValue(true);
    const { unmount } = renderAction([origin]);

    await userEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));
    await waitFor(() => expect(screen.getByTestId("wizard")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /start renewal/i }));

    unmount();
    expect(release).not.toHaveBeenCalled();

    settleRenewal();
    await waitFor(() => expect(release).toHaveBeenCalled());
  });
  it("blocks reopening until the release started at close has settled", async () => {
    const origin = makeMockSubSample({});
    const acquire = vi.spyOn(origin, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    let finishRelease: (released: boolean) => void = () => {};
    vi.spyOn(origin, "releaseLock").mockReturnValue(
      new Promise<boolean>((resolve) => {
        finishRelease = resolve;
      }),
    );
    renderAction([origin]);

    const button = screen.getByRole("button", { name: /operations\.action\.process/i });
    await userEvent.click(button);
    await waitFor(() => expect(screen.getByTestId("wizard")).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: /close wizard/i }));

    await userEvent.click(button);
    expect(acquire).toHaveBeenCalledTimes(1);

    finishRelease(true);
    await waitFor(() => expect(screen.queryByTestId("wizard")).not.toBeInTheDocument());
    await userEvent.click(button);
    await waitFor(() => expect(acquire).toHaveBeenCalledTimes(2));
  });
});
