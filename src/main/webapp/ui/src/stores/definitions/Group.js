//@flow

import { type Id, type GlobalId } from "./BaseRecord";
import { type _LINK } from "../../common/ApiServiceBase";

export type Group = {
  id: Id,
  globalId: GlobalId,
  name: string,
  uniqueName: string,
  _links: Array<_LINK>,
};

export type SharedWithGroup = {|
  group: Group,
  shared: boolean,
  itemOwnerGroup: boolean,
|};
