import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { RecordLockedError } from "@/stores/models/InventoryBaseRecord";
import materialTheme from "@/theme";
import { lockOwnerName } from "../lockAlerts";
import ProcessAction, { isProcessableSelection } from "../ProcessAction";

// The wizard itself is under test elsewhere; here only the menu entry and the lock it takes on the
// way in matter, so the wizard is a marker that says whether it opened.
vi.mock("../../Operations/OperationWizard", () => ({
  default: ({ open }: { open: boolean }) => (open ? <div data-testid="wizard" /> : null),
}));
const addAlert = vi.fn();
// The subsample model reads the root store (units); a minimal stand-in suffices.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) }, uiStore: { addAlert } }),
}));

describe("isProcessableSelection", () => {
  // The single gate shared by ContextActions visibility and the action's own wizard mounting.
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

/** The wizard holds the edit-session lock on each origin for as long as it is open (RSDEV-1231). */
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

  it("refuses to open when someone else holds an origin, and gives back what it took", async () => {
    const free = makeMockSubSample({});
    const held = makeMockSubSample({ id: 2, globalId: "SS2" });
    vi.spyOn(free, "acquireEditLock").mockResolvedValue("LOCKED_OK");
    const release = vi.spyOn(free, "releaseLock").mockResolvedValue(true);
    vi.spyOn(held, "acquireEditLock").mockRejectedValue(new RecordLockedError(held, holder));
    renderAction([free, held]);

    await userEvent.click(screen.getByRole("button", { name: /operations\.action\.process/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(screen.queryByTestId("wizard")).not.toBeInTheDocument();
    expect(release).toHaveBeenCalled();
    const details = (addAlert.mock.calls[0][0] as { details: Array<{ record: unknown }> }).details;
    expect(details).toHaveLength(1);
    expect(details[0].record).toBe(held);
    expect(lockOwnerName(holder)).toBe("Carol Holder");
  });
});
