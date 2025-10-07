import { type Id, type GlobalId } from "./BaseRecord";
import { type _LINK } from "../../util/types";

/**
 * A simple definition of a group of users (Lab group, Collaboration group,
 * etc), sufficient for use in Inventory.
 */
export type Group = {
  id: Id;
  globalId: GlobalId;
  name: string;
  uniqueName: string;
  _links: Array<_LINK>;
};

export type SharedWithGroup = {
  group: Group;
  shared: boolean;
  itemOwnerGroup: boolean;
};
