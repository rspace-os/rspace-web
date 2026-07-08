import { action, makeObservable, observable } from "mobx";
import i18n from "@/modules/common/i18n";
import ApiService from "../../common/InvApiService";
import { showToastWhilstPending } from "../../util/alerts";
import { getErrorMessage } from "../../util/error";
import type { URL } from "../../util/types";
import { mkAlert } from "../contexts/Alert";
import type { GlobalId, Id } from "../definitions/BaseRecord";
import type { Basket, BasketAttrs, BasketDetails } from "../definitions/Basket";
import type { InventoryRecord } from "../definitions/InventoryRecord";
import getRootStore from "../stores/getRootStore";

/**
 * This class models any basket (collection of items)
 * stored in the Inventory system.
 */
export default class BasketModel implements Basket {
  name: string;
  id: Id;
  globalId: GlobalId;
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
          message: i18n.t("inventory:baskets.alerts.getContentsFailed"),
          variant: "error",
        }),
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
        const itemsToAdd = itemIds.filter((itemId) => !basketItemIds.includes(itemId));
        const itemsCount = itemsToAdd.length;
        if (itemsCount > 0 && typeof this.id === "number") {
          const res = await showToastWhilstPending(
            i18n.t("inventory:baskets.pending.addItems", { count: itemsCount }),
            ApiService.post<void>(`baskets/${this.id}/addItems`, {
              globalIds: itemsToAdd,
            }),
          );
          if (res.status === 200) {
            // refetch to update basket items list
            await searchStore.search.fetcher.reperformCurrentSearch();
            uiStore.addAlert(
              mkAlert({
                message: i18n.t("inventory:baskets.alerts.itemsAdded", { count: itemsCount, name: this.name }),
                variant: "success",
              }),
            );
            // refetch to update item count(s)
            await searchStore.getBaskets();
          }
        } else {
          uiStore.addAlert(
            mkAlert({
              message: i18n.t("inventory:baskets.alerts.itemsAlreadyAdded", { name: this.name }),
              variant: "warning",
            }),
          );
        }
      } else {
        throw new Error("Cannot add items to a Basket without id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:baskets.alerts.addItemsFailed"),
          message: getErrorMessage(e, ""),
          variant: "error",
        }),
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
          i18n.t("inventory:baskets.pending.removeItems", { count: itemsCount }),
          ApiService.post<void>(`baskets/${this.id}/removeItems`, {
            globalIds: itemIds,
          }),
        );
        if (res.status === 200) {
          // refetch to update basket items list
          await searchStore.search.fetcher.reperformCurrentSearch();
          uiStore.addAlert(
            mkAlert({
              message: i18n.t("inventory:baskets.alerts.itemsRemoved", { count: itemsCount, name: this.name }),
              variant: "success",
            }),
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
          title: i18n.t("inventory:baskets.alerts.removeItemsFailed"),
          message: (e as Error).message || "",
          variant: "error",
        }),
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
          i18n.t("inventory:baskets.pending.updateDetails"),
          ApiService.put<void>(`baskets/${this.id}`, details),
        );
        uiStore.addAlert(
          mkAlert({
            message: i18n.t("inventory:baskets.alerts.detailsUpdated"),
            variant: "success",
          }),
        );
        // refetch to update detail(s)
        await searchStore.getBaskets();
      } else {
        throw new Error("Cannot update Basket without id");
      }
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:baskets.alerts.updateDetailsFailed"),
          message: (e as Error).message || "",
          variant: "error",
        }),
      );
    } finally {
      this.setLoading(false);
    }
  }
}

/*
 * A factory rather than a shared const so the localized name resolves when a
 * dialog mounts (after i18n has initialised and the inventory namespace has
 * loaded) instead of being frozen to whatever was available at import time.
 */
export const getNewBasket = (): Basket =>
  new BasketModel({
    name: i18n.t("inventory:baskets.newBasket"),
    id: null,
    globalId: "",
    items: [],
    itemCount: 0,
    _links: [],
  });
