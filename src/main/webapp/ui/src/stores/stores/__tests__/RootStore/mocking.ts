// biome-ignore lint/style/useImportType: initial biome migration
import AuthStore from "../../AuthStore";
// biome-ignore lint/style/useImportType: initial biome migration
import ImageStore from "../../ImageStore";
// biome-ignore lint/style/useImportType: initial biome migration
import ImportStore from "../../ImportStore";
// biome-ignore lint/style/useImportType: initial biome migration
import MaterialsStore from "../../MaterialsStore";
// biome-ignore lint/style/useImportType: initial biome migration
import MoveStore from "../../MoveStore";
// biome-ignore lint/style/useImportType: initial biome migration
import PeopleStore from "../../PeopleStore";
import type { StoreContainer } from "../../RootStore";
// biome-ignore lint/style/useImportType: initial biome migration
import SearchStore from "../../SearchStore";
// biome-ignore lint/style/useImportType: initial biome migration
import TrackingStore from "../../TrackingStore";
// biome-ignore lint/style/useImportType: initial biome migration
import UiStore from "../../UiStore";
// biome-ignore lint/style/useImportType: initial biome migration
import UnitStore from "../../UnitStore";

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
