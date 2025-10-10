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
    /*
     * This function is re-assigned to when the analytics library has been loaded.
     */
    this.trackEvent = () => {};
  }
}
