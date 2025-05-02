import { makeObservable, observable, action } from "mobx";
import {
  type Basket,
  type BasketAttrs,
  type BasketDetails,
} from "../definitions/Basket";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import ApiService from "../../common/InvApiService";
import { type URL } from "../../util/types";
import getRootStore from "../stores/RootStore";
import { mkAlert } from "../contexts/Alert";
import { showToastWhilstPending } from "../../util/alerts";

/*
 * This class models any basket (collection of items)
 * stored in the Inventory system.
 */
export default class BasketModel implements Basket {
  name: string;
  id: ?Id;
  globalId: ?GlobalId;
  items: Array<InventoryRecord>;
  itemCount: number;
  _links: Array<URL>;
  loading: boolean = false;

  constructor(attrs: BasketAttrs) {
    makeObservable(this, {
      id: observable,
      name: observable,
      globalId: observable,
      items: observable,
      itemCount: observable,
      _links: observable,
      loading: observable,
      setLoading: action,
      getItems: action,
      addItems: action,
      removeItems: action,
      updateDetails: action,
    });
    this.id = attrs.id;
    this.name = attrs.name;
    this.globalId = attrs.globalId;
    this.items = attrs.items || [];
    this.itemCount = attrs.itemCount;
    this._links = attrs._links;
  }

  setLoading(value: boolean) {
    this.loading = value;
  }

  async getItems(): Promise<void> {
    const { uiStore, searchStore } = getRootStore();
    try {
      this.setLoading(true);
      const fullBasket = await searchStore.getBasket(this.id);
      if (fullBasket) {
        this.items = fullBasket.items;
        this.itemCount = fullBasket.itemCount;
      }
    } catch {
      uiStore.addAlert(
        mkAlert({
          message: `Error getting Basket contents.`,
          variant: "error",
        })
      );
    } finally {
      this.setLoading(false);
    }
  }

  async addItems(itemIds: Array<GlobalId>): Promise<void> {
    const { uiStore, searchStore } = getRootStore();
    try {
      this.setLoading(true);
      if (this.id) {
        const basketItemIds = this.items.map((i) => i.globalId);
        const itemsToAdd = itemIds.filter(
          (itemId) => !basketItemIds.includes(itemId)
        );
        const itemsCount = itemsToAdd.length;
        if (itemsCount > 0 && typeof this.id === "number") {
          const res = await showToastWhilstPending(
            `Adding item${itemsCount > 1 ? "s" : ""} to Basket...`,
            ApiService.post<{| globalIds: typeof itemsToAdd |}, void>(
              `baskets/${this.id}/addItems`,
              {
                globalIds: itemsToAdd,
              }
            )
          );
          if (res.status === 200) {
            // refetch to update basket items list
            await searchStore.search.fetcher.reperformCurrentSearch();
            uiStore.addAlert(
              mkAlert({
                message: `Item${
                  itemsCount > 1 ? "s" : ""
                } successfully added to ${this.name}.`,
                variant: "success",
              })
            );
            // refetch to update item count(s)
            await searchStore.getBaskets();
          }
        } else {
          uiStore.addAlert(
            mkAlert({
              message: `The selected items are in ${this.name} already. No items were added.`,
              variant: "warning",
            })
          );
        }
      } else {
        throw new Error("Cannot add items to a Basket without id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error adding items to Basket.",
          message: e.response?.data.message ?? e.message ?? "",
          variant: "error",
        })
      );
    } finally {
      this.setLoading(false);
    }
  }

  async removeItems(itemIds: Array<GlobalId>): Promise<void> {
    const { uiStore, searchStore } = getRootStore();
    try {
      this.setLoading(true);
      if (this.id) {
        const itemsCount = itemIds.length;
        const res = await showToastWhilstPending(
          `Removing item${itemsCount > 1 ? "s" : ""} from Basket...`,
          ApiService.post<{| globalIds: typeof itemIds |}, void>(
            `baskets/${this.id}/removeItems`,
            {
              globalIds: itemIds,
            }
          )
        );
        if (res.status === 200) {
          // refetch to update basket items list
          await searchStore.search.fetcher.reperformCurrentSearch();
          uiStore.addAlert(
            mkAlert({
              message: `Item${
                itemsCount > 1 ? "s" : ""
              } successfully removed from ${this.name}.`,
              variant: "success",
            })
          );
          // refetch to update item count(s)
          await searchStore.getBaskets();
        }
      } else {
        throw new Error("Cannot remove items without a Basket id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error removing items from Basket.",
          message: e.message || "",
          variant: "error",
        })
      );
    } finally {
      this.setLoading(false);
    }
  }

  async updateDetails(details: BasketDetails): Promise<void> {
    const { uiStore, searchStore } = getRootStore();
    try {
      this.setLoading(true);
      if (this.id) {
        await showToastWhilstPending(
          `Updating Basket details...`,
          ApiService.put<BasketDetails, void>(`baskets/${this.id}`, details)
        );
        uiStore.addAlert(
          mkAlert({
            message: `Basket details updated.`,
            variant: "success",
          })
        );
        // refetch to update detail(s)
        await searchStore.getBaskets();
      } else {
        throw new Error("Cannot update Basket without id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Error updating Basket details.",
          message: e.message || "",
          variant: "error",
        })
      );
    } finally {
      this.setLoading(false);
    }
  }
}

export const NEW_BASKET: Basket = new BasketModel({
  name: "New Basket",
  id: null,
  globalId: "",
  items: [],
  itemCount: 0,
  _links: [],
});
