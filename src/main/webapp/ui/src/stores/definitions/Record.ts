/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

import { type URL } from "../../util/types";
import { type Container } from "./Container";
import { type Person } from "../definitions/Person";
import { type InventoryRecord } from "./InventoryRecord";
import { type Tag } from "./Tag";
import { type LinkableRecord } from "./LinkableRecord";

export type ReadAccessLevel = "full" | "limited" | "public";

/**
 * A URL to a small image to be displayed alongside summary information.
 */
export type Thumbnail = URL | null;

/**
 * An assortment of key-value pairs, this interface defines a loose collection
 * of data that is rendered by the UI when displaying summary information such
 * as in info popups.
 *
 * All pairings are optional and wont be applicable to the majority of
 * implementations; each of which should just return the relevant data.
 */
export type RecordDetails = {
  /*
   * There are some record types where we don't want to show the Global Id in
   * the UI because the user need not be aware of it; its essentially just an
   * implementation detail and may just confuse them. For example, attachments
   */
  hideGlobalId?: boolean;

  modified?: [string, string];
  owner?: string | null;
  description?: string | null;
  tags?: Array<Tag>;
  quantity?: string;
  contents?: Container;
  location?: InventoryRecord;
  size?: number;
  sample?: InventoryRecord;
  version?: number;
  galleryFile?: LinkableRecord;
};

/**
 * This interface describes Inventory-specific records, but in a more generic
 * sense than InventoryRecord. This includes various pieces of data that may
 * be manipulated by the Inventory system and have state independent of any of
 * the main record types (sample, container, etc) that they may be associated
 * with. This includes Attachments as well as all InventoryRecords.
 */
export interface Record extends LinkableRecord {
  owner: Person | null;
  thumbnail?: Thumbnail;

  readonly deleted: boolean;
  readonly cardTypeLabel: string;
  readonly currentUserIsOwner?: boolean | null;
  readonly readAccessLevel?: ReadAccessLevel;

  readonly recordDetails: RecordDetails;
}
