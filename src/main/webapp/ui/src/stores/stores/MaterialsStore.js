// @flow

import InvApiService from "../../common/InvApiService";
import { mkAlert } from "../contexts/Alert";
import {
  ListOfMaterials,
  type ListOfMaterialsAttrs,
  type ListOfMaterialsId,
  type ElnFieldId,
  type ElnDocumentId,
} from "../models/MaterialsModel";
import type { RootStore } from "./RootStore";
import {
  action,
  computed,
  observable,
  makeObservable,
  runInAction,
} from "mobx";
import RsSet, { unionWith } from "../../util/set";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";

type FieldLists = Map<ElnFieldId, Array<ListOfMaterials>>;
type DocumentLists = Map<ElnDocumentId, Array<ListOfMaterials>>;

export default class MaterialsStore {
  rootStore: RootStore;
  loading: boolean = false;
  originalList: ?ListOfMaterials;
  currentList: ?ListOfMaterials;
  fieldLists: FieldLists;
  documentLists: DocumentLists;
  setupPromise: ?Promise<void> = null; // access only through this.setup

  /*
   * This is globally set for all LoM if it can be determined based on whether
   * the user can edit the entire page. Otherwise, it is null, and every LoM
   * that user wishes to see should be checked against the API.
   */
  canEdit: ?boolean = null;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      loading: observable,
      originalList: observable,
      currentList: observable,
      fieldLists: observable,
      documentLists: observable,
      canEdit: observable,
      setup: action,
      getDocumentMaterialsListings: action,
      getFieldMaterialsListings: action,
      getMaterialsListing: action,
      newListOfMaterials: action,
      replaceListInField: action,
      setCurrentList: action,
      setLoading: action,
      isListValid: computed,
      isListEditing: computed,
      isListNew: computed,
      isListExisting: computed,
      hasListChanged: computed,
      isCurrentListUnchanged: computed,
      hasListEnoughLeft: computed,
      cantSaveCurrentList: computed,
      allInvRecordsFromAllDocumentLists: computed,
    });
    this.rootStore = rootStore;
    this.fieldLists = new Map();
    this.documentLists = new Map();
  }

  /*
   * This is any and all setup that must be done by any, but only one,
   *  MaterialListing
   */
  setup(): Promise<void> {
    if (this.setupPromise) return this.setupPromise;
    this.setupPromise = this.rootStore.authStore
      .synchronizeWithSessionStorage()
      .then(() => {
        void this.rootStore.peopleStore.fetchCurrentUser();
        void this.rootStore.unitStore.fetchUnits();
      });
    return this.setupPromise;
  }

  setLoading(value: boolean): void {
    this.loading = value;
  }

  /*
   * All of the data associated with a particular ELN Structured Document
   */
  async getDocumentMaterialsListings(documentId: ElnDocumentId): Promise<void> {
    this.setLoading(true);

    try {
      const { data } = await InvApiService.get<
        mixed,
        Array<ListOfMaterialsAttrs>
      >(`listOfMaterials/forDocument`, documentId);
      runInAction(() => {
        this.documentLists.set(
          documentId,
          data.map((d) => new ListOfMaterials(d))
        );
      });
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not fetch List of Materials data.`,
          message: error?.response?.data.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error(
        `Error fetching Lists of Materials for document ${documentId}`,
        error
      );
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  /*
   * All of the data associated with a particular ELN Text Field
   */
  async getFieldMaterialsListings(fieldId: ElnFieldId): Promise<void> {
    this.setLoading(true);

    try {
      const { data } = await InvApiService.get<
        void,
        Array<ListOfMaterialsAttrs>
      >(`listOfMaterials/forField`, fieldId);
      runInAction(() => {
        this.fieldLists.set(
          fieldId,
          data.map((d) => new ListOfMaterials(d))
        );
      });
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not fetch List of Materials data.`,
          message:
            error?.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error(
        `Error fetching Lists of Materials for field ${fieldId}`,
        error
      );
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  async getMaterialsListing(id: ListOfMaterialsId): Promise<ListOfMaterials> {
    this.setLoading(true);

    if (!id) throw new Error("A list without id cannot be fetched.");
    try {
      const { data } = await InvApiService.get<void, ListOfMaterialsAttrs>(
        `listOfMaterials`,
        id
      );
      const newList = new ListOfMaterials(data);
      this.setCurrentList(newList);
      return newList;
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not fetch List of Materials data.`,
          message:
            error?.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error(`Error fetching Lists of Materials for field ${id}`, error);
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  newListOfMaterials(elnFieldId: ElnFieldId): void {
    const fieldLists = this.fieldLists.get(elnFieldId);
    const listCount = fieldLists?.length;
    const listNames = fieldLists?.map((list) => list.name);
    const newName = `List #${(listCount ?? 0) + 1}`;
    const makeNewName = () => (listNames?.includes(newName) ? "List" : newName);

    const newLoM = new ListOfMaterials({
      id: null,
      elnFieldId,
      name: makeNewName(),
      description: "",
      materials: [],
    });
    this.currentList = newLoM;
  }

  replaceListInField(list: ListOfMaterials, fieldId: ElnFieldId) {
    const fl = this.fieldLists.get(fieldId);
    if (!fl) return;
    const i = fl.findIndex((l) => l.id === list.id);
    fl.splice(i, 1, list);
    this.fieldLists.set(fieldId, fl);
  }

  setCurrentList(list: ?ListOfMaterials) {
    if (list) this.replaceListInField(list, list.elnFieldId);
    this.originalList = list?.clone();
    this.currentList = list;
  }

  get isListValid(): boolean {
    return Boolean(this.currentList?.isValid);
  }

  get isListEditing(): boolean {
    return Boolean(this.currentList?.editingMode);
  }

  get isListNew(): boolean {
    return !this.currentList?.id;
  }

  get isListExisting(): boolean {
    return this.currentList?.id !== null;
  }

  get hasListChanged(): boolean {
    if (!this.currentList || !this.originalList)
      throw new Error("Cannot check changes to null Lists");
    return (
      !this.currentList.isEqual(this.originalList) ||
      Boolean(this.currentList?.materials.some((m) => m.usedQuantityChanged))
    );
  }

  get hasListEnoughLeft(): boolean {
    return Boolean(this.currentList?.enoughLeft);
  }

  async fetchCanEdit(lomId: ListOfMaterialsId): Promise<boolean> {
    if (!lomId) return true;
    try {
      const { data } = await InvApiService.get<void, boolean>(
        `/listOfMaterials/${lomId}/canEdit`
      );
      return data;
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not fetch permission to edit.`,
          message:
            error?.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error("canEdit call failed", error);
      return false;
    }
  }

  get isCurrentListUnchanged(): boolean {
    return (
      this.isListExisting &&
      (!this.currentList || !this.originalList || !this.hasListChanged)
    );
  }

  get cantSaveCurrentList(): ValidationResult {
    if (!this.currentList?.canEdit)
      return IsInvalid("You do not have permission to save edits.");
    if (this.isCurrentListUnchanged) return IsInvalid("No changes to save.");
    if (!this.isListValid) return IsInvalid("List is invalid.");
    if (!this.currentList.validAdditionalAmount)
      return IsInvalid("Invalid used quantity changes.");
    if (!this.hasListEnoughLeft)
      return IsInvalid(
        "One of the subsamples does not have sufficient quantity left."
      );
    return IsValid();
  }

  get allInvRecordsFromAllDocumentLists(): RsSet<InventoryRecord> {
    const lists = [...this.documentLists.values()].flat();
    return unionWith(
      ({ globalId }) => globalId,
      lists.map(
        ({ materials }) => new RsSet(materials.map(({ invRec }) => invRec))
      )
    );
  }
}
