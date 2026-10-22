import "@/stores/stores/RootStore";
import fc from "fast-check";
import { describe, expect, test, vi } from "vitest";
import { arbitraryGroup } from "../../../definitions/__tests__/Group/helper";
import getRootStore from "../../getRootStore";

describe("method: createNewInstrument", () => {
  test("Should be shared with the owner's groups by default", async () => {
    await fc.assert(
      fc.asyncProperty(fc.array(arbitraryGroup), async (groups) => {
        const { searchStore, peopleStore } = getRootStore();
        vi.spyOn(peopleStore, "fetchCurrentUsersGroups").mockImplementation(() => Promise.resolve(groups));
        const instrument = await searchStore.createNewInstrument();
        expect(instrument.sharingMode).toBe("OWNER_GROUPS");
        if (!instrument.sharedWith) throw new Error("sharedWith should be set");
        expect(instrument.sharedWith.map(({ group }) => group)).toEqual(groups);
        expect(instrument.sharedWith.every(({ shared, itemOwnerGroup }) => shared && itemOwnerGroup)).toBe(true);
      }),
    );
  });
});
