import fc, { type Arbitrary } from "fast-check";
import type { Group, SharedWithGroup } from "../../Group";
import { arbitraryGlobalId, arbitraryId } from "../Record/helper";

export const arbitraryGroup: Arbitrary<Group> = fc.record({
    id: arbitraryId,
    globalId: arbitraryGlobalId("GP"),
    name: fc.string(),
    uniqueName: fc.string(),
    _links: fc.constant([]),
});

export const arbitrarySharedWithGroup: Arbitrary<SharedWithGroup> = fc.record({
    group: arbitraryGroup,
    shared: fc.boolean(),
    itemOwnerGroup: fc.boolean(),
});
