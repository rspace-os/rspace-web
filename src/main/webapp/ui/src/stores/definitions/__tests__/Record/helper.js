//@flow

import fc, { type Arbitrary } from "fast-check";
import { type Id, type GlobalId, type GlobalIdPrefix } from "../../BaseRecord";
import { type Record as Foo } from "../../Record";
import { arbitraryPerson } from "../Person/helper";

export const arbitraryId: Arbitrary<Id> = fc
  .integer({ min: 1 })
  .map((n) => (n: ?number));

export const arbitraryGlobalId = (
  prefix: GlobalIdPrefix
): Arbitrary<GlobalId> =>
  arbitraryId.map((id) => {
    if (!id) throw new Error("id must not be null");
    return `${prefix}${id}`;
  });

export const arbitraryRecord: Arbitrary<Foo> = fc
  .record<any>({
    id: arbitraryId,

    /*
     * To keep the code simple, all generated Global IDs will be those for
     * samples. If this arbitrary ends up being used all over, it will be
     * desirable to come up with a way to generate the prefix too.
     */
    globalId: arbitraryGlobalId("SA"),

    iconName: fc.constant("sample"),
    recordTypeLabel: fc.string(),

    owner: arbitraryPerson,
    deleted: fc.boolean(),
    cardTypeLabel: fc.string(),
    permalink: fc.option(fc.string()),
    recordDetails: fc.constant({}),
  })
  .map((arbs) => ({
    ...arbs,
  }));
