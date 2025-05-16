import { type RootStore } from "./RootStore";
import { makeObservable, observable } from "mobx";

export default class TrackingStore {
  rootStore: RootStore;
  trackEvent: (event: string, properties?: Record<string, unknown>) => void;

  constructor(rootStore: RootStore) {
    this.rootStore = rootStore;
    makeObservable(this, {
      trackEvent: observable,
    });
    this.trackEvent = () => {};
  }
}
