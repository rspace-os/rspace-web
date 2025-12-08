import AuthStore from "./AuthStore";
import ImageStore from "./ImageStore";
import ImportStore from "./ImportStore";
import MaterialsStore from "./MaterialsStore";
import MoveStore from "./MoveStore";
import PeopleStore from "./PeopleStore";
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
export default function getRootStore(): StoreContainer {
    if (!rootStore) {
        rootStore = new RootStore();
    }
    return rootStore.getStores;
}
