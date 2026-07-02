import AuthStore from "./AuthStore";
import ImageStore from "./ImageStore";
import ImportStore from "./ImportStore";
import MaterialsStore from "./MaterialsStore";
import MoveStore from "./MoveStore";
import PeopleStore from "./PeopleStore";
import { registerRootStore } from "./rootStoreRegistry";
import SearchStore from "./SearchStore";
import TrackingStore from "./TrackingStore";
import UiStore from "./UiStore";
import UnitStore from "./UnitStore";

export type StoreContainer = {
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

let rootStore: undefined | RootStore;

/**
 * Register the lazy singleton factory with the registry. Consumers import
 * `getRootStore` from `./getRootStore` (which reads from the registry); this
 * side effect runs when application/test bootstrap imports this module, wiring
 * up the concrete construction without the accessor pulling in the store graph.
 */
registerRootStore((): StoreContainer => {
  if (!rootStore) {
    rootStore = new RootStore();
  }
  return rootStore.getStores;
});
