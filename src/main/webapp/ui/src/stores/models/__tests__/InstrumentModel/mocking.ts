import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import InstrumentModel, { type InstrumentAttrs } from "../../InstrumentModel";

export const instrumentAttrs = (attrs?: Readonly<Partial<InstrumentAttrs>>): InstrumentAttrs => ({
  id: 1,
  type: "INSTRUMENT",
  globalId: "IN1",
  name: "An instrument",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
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

export const makeMockInstrument = (attrs?: Readonly<Partial<InstrumentAttrs>>): InstrumentModel =>
  new InstrumentModel(new AlwaysNewFactory(), instrumentAttrs(attrs));
