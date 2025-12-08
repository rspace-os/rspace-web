import type AuthStore from "../../AuthStore";
import type ImageStore from "../../ImageStore";
import type ImportStore from "../../ImportStore";
import type MaterialsStore from "../../MaterialsStore";
import type MoveStore from "../../MoveStore";
import type PeopleStore from "../../PeopleStore";
import type { StoreContainer } from "../../RootStore";
import type SearchStore from "../../SearchStore";
import type TrackingStore from "../../TrackingStore";
import type UiStore from "../../UiStore";
import type UnitStore from "../../UnitStore";

export type MockStores = Partial<{ [key in keyof StoreContainer]: object }>;

export const makeMockRootStore = (mockData: MockStores | null): StoreContainer => ({
    authStore: (mockData?.authStore as AuthStore) ?? {},
    uiStore: (mockData?.uiStore as UiStore) ?? {},
    unitStore: (mockData?.unitStore as UnitStore) ?? {},
    searchStore: (mockData?.searchStore as SearchStore) ?? {},
    peopleStore: (mockData?.peopleStore as PeopleStore) ?? {},
    moveStore: (mockData?.moveStore as MoveStore) ?? {},
    trackingStore: (mockData?.trackingStore as TrackingStore) ?? {},
    importStore: (mockData?.importStore as ImportStore) ?? {},
    imageStore: (mockData?.imageStore as ImageStore) ?? {},
    materialsStore: (mockData?.materialsStore as MaterialsStore) ?? {},
});
