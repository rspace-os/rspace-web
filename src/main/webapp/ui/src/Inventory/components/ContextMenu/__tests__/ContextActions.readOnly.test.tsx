import { describe, expect, it, vi } from "vitest";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { menuIDs } from "@/util/menuIDs";
import contextActions from "../ContextActions";

const readOnly = vi.hoisted(() => ({ reason: null as string | null }));

// The action components are only constructed here, never rendered.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { getUnit: () => ({ label: "ml" }) },
    peopleStore: {
      get inventoryReadOnlyReason() {
        return readOnly.reason;
      },
    },
  }),
}));

function disabledReasons(): Record<string, string> {
  const entries = contextActions({
    selectedResults: [makeMockSubSample({})],
    closeMenu: () => {},
    menuID: menuIDs.RESULTS,
    basketSearch: true,
    processAvailable: true,
  })("menuitem");
  return Object.fromEntries(
    entries.map((entry) => [String(entry.component.key), (entry.component.props as { disabled: string }).disabled]),
  );
}

describe("ContextActions while Inventory is read-only", () => {
  it("disables every write action with the reason, but not select, export or barcode printing", () => {
    readOnly.reason = "License expired";
    expect(disabledReasons()).toEqual({
      select: "",
      edit: "License expired",
      create: "License expired",
      process: "License expired",
      duplicate: "License expired",
      move: "License expired",
      transfer: "License expired",
      restore: "License expired",
      "add to basket": "License expired",
      "remove from basket": "License expired",
      export: "",
      "print barcode": "",
      delete: "License expired",
    });
  });

  it("leaves every action enabled when Inventory is writable", () => {
    readOnly.reason = null;
    expect(new Set(Object.values(disabledReasons()))).toEqual(new Set([""]));
  });
});
