import type { URL } from "../../util/types";
import type { BaseRecord, GlobalId, Id } from "./BaseRecord";
import type { InventoryRecord } from "./InventoryRecord";

/**
 * @module Basket
 * @description A Basket is a temporary collection of Inventory records that a user
 * can add to while browsing the system. The basket is persistent and tied to
 * the user account, so it is available across sessions. It is intended to
 * facilitate workflows where a user may want to gather together a set of
 * samples and containers from across the system in order to perform some
 * operation on them as a group, such as moving them to a new location, or
 * adding a common tag to all of them.
 *
 * Baskets are not shared between users and are not intended to be a way of
 * collaborating or sharing data. They are simply a convenience for individual
 * users to help them manage their work.
 *
 * The Basket has no special properties beyond being a collection of Inventory
 * records. It is not a special type of Inventory record itself, nor does it
 * have any special permissions or access controls. It is simply a collection
 * of references to Inventory records that the user has added to their basket.
 */

export type BasketAttrs = {
    id: Id;
    globalId: GlobalId;
    name: string;
    items: Array<InventoryRecord>;
    itemCount: number;
    _links: Array<URL>;
};

export type BasketDetails = {
    name: string;
};

export interface Basket extends BaseRecord {
    items: Array<InventoryRecord>;
    itemCount: number;
    _links: Array<URL>;
    loading: boolean;

    addItems(itemIds: Array<GlobalId>): Promise<void>;
    removeItems(itemIds: Array<GlobalId>): Promise<void>;
    updateDetails(details: BasketDetails): Promise<void>;
}
