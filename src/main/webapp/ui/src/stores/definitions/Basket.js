//@flow

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

import { type BaseRecord, type Id, type GlobalId } from "./BaseRecord";
import { type InventoryRecord } from "./InventoryRecord";
import { type URL } from "../../util/types";

export type BasketAttrs = {|
  id: Id,
  globalId: GlobalId,
  name: string,
  items: Array<InventoryRecord>,
  itemCount: number,
  _links: Array<URL>,
|};

export type BasketDetails = {|
  name: string,
|};

export interface Basket extends BaseRecord {
  items: Array<InventoryRecord>;
  itemCount: number;
  _links: Array<URL>;
  loading: boolean;

  addItems(Array<GlobalId>): Promise<void>;
  removeItems(Array<GlobalId>): Promise<void>;
  updateDetails(BasketDetails): Promise<void>;
}
