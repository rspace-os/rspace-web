import type { RootStore } from "./RootStore";
import Import, { type State } from "../models/ImportModel";
import { action, observable, makeObservable } from "mobx";
import RsSet from "../../util/set";

export type ImportRecordType = "SAMPLES" | "CONTAINERS" | "SUBSAMPLES";

export const IMPORT_PATHNAME = "/inventory/import";

export const isImportPage: () => boolean = () =>
  window.location.pathname === IMPORT_PATHNAME;

export default class ImportStore {
  rootStore: RootStore;
  importData: ?Import = null;
  fileImportKey: number = 0; // whenever this is incremented the file upload field is cleared

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      importData: observable,
      fileImportKey: observable,
      initializeNewImport: action,
    });
    this.rootStore = rootStore;

    if (isImportPage()) {
      const recordType = new URLSearchParams(window.location.search).get(
        "recordType"
      );
      if (
        recordType === "CONTAINERS" ||
        recordType === "SAMPLES" ||
        recordType === "SUBSAMPLES"
      )
        this.initializeNewImport(recordType);
    }
  }

  initializeNewImport(recordType: ImportRecordType) {
    this.importData = new Import(recordType);
    this.fileImportKey++;
  }

  submitImport() {
    if (this.importData) void this.importData.importFiles();
  }

  isCurrentImportState(state: State | RsSet<State>): boolean {
    return this.importData?.state.isCurrentState(state) ?? false;
  }
}
