import { type StoreContainer } from "../../RootStore";
import AuthStore from "../../AuthStore";
import UiStore from "../../UiStore";
import UnitStore from "../../UnitStore";
import SearchStore from "../../SearchStore";
import PeopleStore from "../../PeopleStore";
import MoveStore from "../../MoveStore";
import TrackingStore from "../../TrackingStore";
import ImportStore from "../../ImportStore";
import ImageStore from "../../ImageStore";
import MaterialsStore from "../../MaterialsStore";

//eslint-disable-next-line no-unused-vars
export type MockStores = Partial<{ [key in keyof StoreContainer]: object }>;

export const makeMockRootStore = (
  mockData: MockStores | null
): StoreContainer => ({
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
