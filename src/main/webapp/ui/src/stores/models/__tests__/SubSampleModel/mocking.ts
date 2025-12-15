import SubSampleModel, { type SubSampleAttrs } from "../../SubSampleModel";
import { makeMockContainer } from "../ContainerModel/mocking";
import { type ContainerAttrs } from "../../ContainerModel";
import { makeMockSample } from "../SampleModel/mocking";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import fc, { type Arbitrary } from "fast-check";

/*
 * Note that providing a sample is required
 */
export const subsampleAttrs = (
  attrs?: Readonly<Partial<SubSampleAttrs>>
): SubSampleAttrs => ({
  id: 1,
  type: "SUBSAMPLE",
  globalId: "SS1",
  name: "A subsample",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  quantity: { numericValue: 1, unitId: 3 },
  extraFields: [],
  description: "",
  tags: null,
  parentContainers: [],
  lastNonWorkbenchParent: null,
  lastMoveDate: null,
  parentLocation: null,
  owner: null,
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: [],
  barcodes: [],
  identifiers: [],
  _links: [],
  ...attrs,
});

export const makeMockSubSample = (
  attrs?: Readonly<Partial<SubSampleAttrs>>
): SubSampleModel => {
  const sample = makeMockSample();
  const subsample = new SubSampleModel(new AlwaysNewFactory(), {
    ...subsampleAttrs(attrs),
    sample,
  });
  sample.subSamples = [subsample];
  sample.subSamplesCount = 1;
  return subsample;
};

export const makeMockSubSampleWithParentContainer = (
  attrs?: Readonly<Partial<SubSampleAttrs>>
): SubSampleModel => {
  const container = makeMockContainer({
    id: 2,
    globalId: "IC2",
  });
  const parent = container.paramsForBackend as unknown as ContainerAttrs;
  parent.parentContainers = [];
  parent.parentLocation = null;
  parent.lastNonWorkbenchParent = null;
  return makeMockSubSample({
    parentContainers: [parent],
    ...attrs,
  });
};

/*
 * This is for use with Property tests.
 *
 * Assume that all properties are randomly generated, even if they are
 * currently not.  Not all properties are currently randomly generated, just to
 * keep the code simple, but if randomly generating other properties is needed,
 * then adjust as apppropiate. Assuming that all tests that use this function
 * have been written correctly and do not depend on the currently fixed values,
 * making the additional properties randomly generated should not cause any
 * failures.
 *
 * The value has to be just the attrs, rather than SubSampleModel, because
 * Arbitraries must be instances of the fast-check Arbitrary class. Instead, if
 * an instanceof SubSampleModel is required then call `makeMockSubSample`
 * inside of the Property testing, passing the randomly generated attrs.
 */
export const subSampleAttrsArbitrary: Arbitrary<SubSampleAttrs> = fc
  .nat(1000)
  .chain((id) =>
    fc.record({
      id: fc.constant(id),
      type: fc.constant("SUBSAMPLE"),
      globalId: fc.constant(`SS${id}`),
      name: fc.string({ minLength: 2, maxLength: 20 }),
      permittedActions: fc.constant(["UPDATE", "CHANGE_OWNER"]),
      quantity: fc.constant({ numericValue: 1, unitId: 3 }),
      extraFields: fc.constant([]),
      description: fc.constant(""),
      tags: fc.constant(null),
      parentContainers: fc.constant([]),
      parentLocation: fc.constant(null),
      lastNonWorkbenchParent: fc.constant(null),
      lastMoveDate: fc.constant(null),
      owner: fc.constant(null),
      created: fc.constant(null),
      deleted: fc.constant(false),
      lastModified: fc.constant(null),
      modifiedByFullName: fc.constant(null),
      attachments: fc.constant([]),
      barcodes: fc.constant([]),
      identifiers: fc.constant([]),
      _links: fc.constant([]),
    })
  );
