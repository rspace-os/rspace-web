import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import InstrumentTemplateModel, { type InstrumentTemplateAttrs } from "../../InstrumentTemplateModel";

export const instrumentTemplateAttrs = (
  attrs?: Readonly<Partial<InstrumentTemplateAttrs>>,
): InstrumentTemplateAttrs => ({
  id: 1,
  type: "INSTRUMENT_TEMPLATE",
  globalId: "NT1",
  name: "An instrument template",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  extraFields: [],
  description: "",
  tags: null,
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

export const makeMockInstrumentTemplate = (
  attrs?: Readonly<Partial<InstrumentTemplateAttrs>>,
): InstrumentTemplateModel => new InstrumentTemplateModel(new AlwaysNewFactory(), instrumentTemplateAttrs(attrs));
