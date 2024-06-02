//@flow

import fc, { type Arbitrary } from "fast-check";
import { arbitraryId, arbitraryGlobalId } from "../Record/helper";
import { type Group, type SharedWithGroup } from "../../Group";

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
