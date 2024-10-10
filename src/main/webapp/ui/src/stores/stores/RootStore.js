// @flow

import AuthStore from "./AuthStore";
import UiStore from "./UiStore";
import UnitStore from "./UnitStore";
import SearchStore from "./SearchStore";
import PeopleStore from "./PeopleStore";
import MoveStore from "./MoveStore";
import TrackingStore from "./TrackingStore";
import ImportStore from "./ImportStore";
import ImageStore from "./ImageStore";
import MaterialsStore from "./MaterialsStore";

export type StoreContainer = {
  authStore: AuthStore,
  uiStore: UiStore,
  unitStore: UnitStore,
  searchStore: SearchStore,
  peopleStore: PeopleStore,
  moveStore: MoveStore,
  trackingStore: TrackingStore,
  importStore: ImportStore,
  imageStore: ImageStore,
  materialsStore: MaterialsStore,
};

class RootStore {
  stores: StoreContainer;
  authStore: AuthStore;
  uiStore: UiStore;
  unitStore: UnitStore;
  searchStore: SearchStore;
  peopleStore: PeopleStore;
  moveStore: MoveStore;
  trackingStore: TrackingStore;
  importStore: ImportStore;
  imageStore: ImageStore;
  materialsStore: MaterialsStore;

  constructor() {
    this.uiStore = new UiStore(this);
    this.authStore = new AuthStore(this);
    this.searchStore = new SearchStore(this);
    this.unitStore = new UnitStore(this);
    this.peopleStore = new PeopleStore(this);
    this.moveStore = new MoveStore(this);
    this.trackingStore = new TrackingStore(this);
    this.importStore = new ImportStore(this);
    this.imageStore = new ImageStore(this);
    this.materialsStore = new MaterialsStore(this);

    this.stores = {
      uiStore: this.uiStore,
      authStore: this.authStore,
      searchStore: this.searchStore,
      unitStore: this.unitStore,
      peopleStore: this.peopleStore,
      moveStore: this.moveStore,
      trackingStore: this.trackingStore,
      importStore: this.importStore,
      imageStore: this.imageStore,
      materialsStore: this.materialsStore,
    };
  }

  get getStores(): StoreContainer {
    return this.stores;
  }
}

export type { RootStore };

let rootStore;
export default function getRootStore(): StoreContainer {
  if (!rootStore) {
    rootStore = new RootStore();
  }
  return rootStore.getStores;
}
