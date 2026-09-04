import { describe, expect, it, vi } from "vitest";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { menuIDs } from "@/util/menuIDs";
import contextActions from "../ContextActions";

// The action components are only constructed here, never rendered; the models they carry read the
// root store for units, so a minimal stand-in is enough.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));

/**
 * Whether the Process entry is offered for this menu and selection. RSDEV-1228 decided Process
 * belongs in every Inventory context menu except the picker (DevDocs/adr/0007); this pins that
 * decision so narrowing or widening it is a deliberate change rather than a silent regression.
 */
function processIsOffered(
  menuID: (typeof menuIDs)[keyof typeof menuIDs],
  selectedResults: Array<InventoryRecord>,
): boolean {
  const entries = contextActions({
    selectedResults,
    closeMenu: () => {},
    menuID,
    basketSearch: false,
  })("menuitem");
  const process = entries.find((entry) => entry.component.key === "process");
  expect(process, "ContextActions no longer has a 'process' entry").toBeDefined();
  return !process?.hidden;
}

describe("ContextActions: the Process entry", () => {
  it("is offered in every menu that acts on records, and never in the picker", () => {
    const selection = [makeMockSubSample({})];
    for (const menuID of [menuIDs.RESULTS, menuIDs.CARD, menuIDs.CONTENT, menuIDs.STEPPER]) {
      expect(processIsOffered(menuID, selection), `expected Process in the ${menuID} menu`).toBe(true);
    }
    // The picker chooses a record for another form; running an operation from it would leave the
    // form pointing at material the operation just consumed.
    expect(processIsOffered(menuIDs.PICKER, selection)).toBe(false);
  });

  it("is offered for one or many subsamples, but not for a selection the wizard cannot act on", () => {
    expect(processIsOffered(menuIDs.RESULTS, [makeMockSubSample({})])).toBe(true);
    expect(
      processIsOffered(menuIDs.RESULTS, [makeMockSubSample({}), makeMockSubSample({ id: 2, globalId: "SS2" })]),
    ).toBe(true);
    // a sample is not an origin the wizard can operate on, so a mixed selection hides the entry
    expect(processIsOffered(menuIDs.RESULTS, [makeMockSubSample({}), makeMockSample()])).toBe(false);
    expect(processIsOffered(menuIDs.RESULTS, [])).toBe(false);
  });

  it("is hidden for a deleted subsample, which holds no material to operate on", () => {
    expect(processIsOffered(menuIDs.RESULTS, [makeMockSubSample({ deleted: true })])).toBe(false);
  });
});
