//@flow

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
export type MockStores = Partial<{[key in keyof StoreContainer]: { ... }}>;

export const makeMockRootStore = (mockData: ?MockStores): StoreContainer => ({
  // $FlowExpectedError[incompatible-cast] Pretend its an AuthStore
  // $FlowExpectedError[incompatible-use] Pretend its an AuthStore
  authStore: (mockData.authStore: AuthStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a UiStore
  // $FlowExpectedError[incompatible-use] Pretend its a UiStore
  uiStore: (mockData.uiStore: UiStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a UnitStore
  // $FlowExpectedError[incompatible-use] Pretend its a UnitStore
  unitStore: (mockData.unitStore: UnitStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a SearchStore
  // $FlowExpectedError[incompatible-use] Pretend its a SearchStore
  searchStore: (mockData.searchStore: SearchStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a PeopleStore
  // $FlowExpectedError[incompatible-use] Pretend its a PeopleStore
  peopleStore: (mockData.peopleStore: PeopleStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a MoveStore
  // $FlowExpectedError[incompatible-use] Pretend its a MoveStore
  moveStore: (mockData.moveStore: MoveStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a TrackingStore
  // $FlowExpectedError[incompatible-use] Pretend its a TrackingStore
  trackingStore: (mockData.trackingStore: TrackingStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its an ImportStore
  // $FlowExpectedError[incompatible-use] Pretend its an ImportStore
  importStore: (mockData.importStore: ImportStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its an ImageStore
  // $FlowExpectedError[incompatible-use] Pretend its an ImageStore
  imageStore: (mockData.imageStore: ImageStore) ?? {},
  // $FlowExpectedError[incompatible-cast] Pretend its a MaterialStore
  // $FlowExpectedError[incompatible-use] Pretend its a MaterialStore
  materialsStore: (mockData.materialsStore: MaterialsStore) ?? {},
});
