//@flow
import TemplateModel, { type TemplateAttrs } from "../../TemplateModel";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";

export const templateAttrs = (
  attrs?: Partial<TemplateAttrs>
): TemplateAttrs => ({
  id: 1,
  type: "SAMPLE_TEMPLATE",
  globalId: "IT1",
  name: "A template",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  templateId: null,
  templateVersion: null,
  subSampleAlias: { alias: "subsample", plural: "subsamples" },
  subSamplesCount: 0,
  subSamples: [],
  quantity: null,
  storageTempMin: { numericValue: 15, unitId: 8 },
  storageTempMax: { numericValue: 30, unitId: 8 },
  fields: [],
  extraFields: [],
  description: "",
  tags: null,
  sampleSource: "LAB_CREATED",
  expiryDate: null,
  iconId: null,
  owner: {
    id: 1,
    username: "jb",
    firstName: "joe",
    lastName: "bloggs",
    hasPiRole: false,
    hasSysAdminRole: false,
    email: null,
    workbenchId: 1,
    _links: [],
  },
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: [],
  _links: [],
  defaultUnitId: 3,
  historicalVersion: false,
  version: 1,
  barcodes: [],
  identifiers: [],
  sharingMode: "OWNER_GROUPS",
  sharedWith: [],
  ...attrs,
});

export const makeMockTemplate = (
  attrs?: Partial<TemplateAttrs>
): TemplateModel =>
  new TemplateModel(new AlwaysNewFactory(), templateAttrs(attrs));
