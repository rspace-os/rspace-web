// @flow

import { action, observable, makeObservable } from "mobx";
import type { RootStore } from "./RootStore";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import Search from "../models/Search";
import { type Location } from "../definitions/Container";
import { menuIDs } from "../../util/menuIDs";

type CreationContext = $Values<typeof menuIDs> | "containerLocation";

export default class CreateStore {
  rootStore: RootStore;
  loading: boolean = false;
  creationContext: CreationContext = "";
  templateCreationContext: $Values<typeof menuIDs> = "";
  submitting: boolean;
  selectedResults: Array<InventoryRecord> = [];
  targetLocation: ?Location;
  targetLocationIdentifier: number;
  search: ?Search;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      loading: observable,
      creationContext: observable,
      templateCreationContext: observable,
      submitting: observable,
      selectedResults: observable,
      search: observable,
      targetLocation: observable,
      targetLocationIdentifier: observable,
      setCreationContext: action,
      setTemplateCreationContext: action,
      setTargetLocation: action,
      setTargetLocationIdentifier: action,
    });
    this.rootStore = rootStore;
  }

  setCreationContext(value: CreationContext) {
    this.creationContext = value;
  }

  setTemplateCreationContext(value: $Values<typeof menuIDs>) {
    this.templateCreationContext = value;
  }

  setTargetLocation(value: Location) {
    this.targetLocation = value;
  }
  // when we use an index to identify the location
  setTargetLocationIdentifier(value: number) {
    this.targetLocationIdentifier = value;
  }
}
