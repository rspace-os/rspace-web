// @flow

import { type GlobalId } from "../definitions/BaseRecord";
import { type LinkableRecord } from "../definitions/LinkableRecord";
import { inventoryRecordTypeLabels, globalIdToInventoryRecordTypeLabel } from "../definitions/InventoryRecord";
import { getByKey } from "../../util/optional";


/**
 * This is a LinkableRecord that can be constructed from just a Global Id. It
 * is useful when the API only returns the Global Id and we wish to display
 * that Global Id in the UI as a link with an icon, but we don't have any other
 * information about the record.
 */
export default class LinkableRecordFromGlobalId implements LinkableRecord {
  globalId: ?GlobalId;
  name: string;
  id: ?number;

  constructor(globalId: GlobalId) {
    this.globalId = globalId;
    this.name = "";
    this.id = parseInt(globalId.substring(2), 10);
  }

  get recordTypeLabel(): string {
    if (!this.globalId) throw new Error("Impossible");
    return globalIdToInventoryRecordTypeLabel(this.globalId);
  }

  get iconName(): string {
    if (!this.globalId) throw new Error("Impossible");
    return getByKey(this.globalId.substring(0, 2), {
      "IC": "container",
      "SA": "sample",
      "SS": "subsample",
      "IT": "template",
    }).orElseGet(() => {
      throw new Error("Not an Inventory Record Type");
    });
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}
