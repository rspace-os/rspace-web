//@flow
import ContainerModel, { type ContainerAttrs } from "../../ContainerModel";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import { personAttrs } from "../PersonModel/mocking";

export const containerAttrs = (
  attrs?: $ReadOnly<Partial<ContainerAttrs>>
): ContainerAttrs => ({
  id: 1,
  type: "CONTAINER",
  globalId: "IC1",
  name: "A list container",
  canStoreContainers: true,
  canStoreSamples: true,
  description: "",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  quantity: null,
  tags: null,
  locations: [],
  gridLayout: null,
  cType: "LIST",
  locationsCount: null,
  contentSummary: {
    totalCount: 0,
    subSampleCount: 0,
    containerCount: 0,
  },
  parentContainers: [],
  lastNonWorkbenchParent: null,
  lastMoveDate: null,
  owner: null,
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: [],
  barcodes: [],
  identifiers: [],
  sharingMode: "OWNER_GROUPS",
  sharedWith: [],
  _links: [],
  ...attrs,
});

export const makeMockContainer = (
  attrs?: $ReadOnly<Partial<ContainerAttrs>>
): ContainerModel =>
  new ContainerModel(new AlwaysNewFactory(), containerAttrs(attrs));

export const benchAttrs = ({
  owner,
  ...attrs
}: $ReadOnly<Partial<ContainerAttrs>>): ContainerAttrs => ({
  id: 1,
  type: "CONTAINER",
  globalId: "BE1",
  name: "WB user1a",
  canStoreContainers: true,
  canStoreSamples: true,
  description: "",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  quantity: null,
  tags: null,
  locations: [],
  gridLayout: null,
  cType: "WORKBENCH",
  locationsCount: 0,
  contentSummary: {
    totalCount: 0,
    subSampleCount: 0,
    containerCount: 0,
  },
  parentContainers: [],
  lastNonWorkbenchParent: null,
  lastMoveDate: null,
  owner: owner === null ? owner : personAttrs(owner),
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: [],
  barcodes: [],
  sharingMode: "OWNER_GROUPS",
  sharedWith: [],
  _links: [],
  identifiers: [],
  ...attrs,
});

export const makeMockBench = (attrs: Partial<ContainerAttrs>): ContainerModel =>
  new ContainerModel(new AlwaysNewFactory(), benchAttrs(attrs));
