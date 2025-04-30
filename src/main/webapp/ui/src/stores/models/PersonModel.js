//@flow

import ApiService from "../../common/InvApiService";
import { type _LINK } from "../../util/types";
import { type Container, type WorkbenchId } from "../definitions/Container";
import getRootStore from "../stores/RootStore";
import {
  observable,
  computed,
  makeObservable,
  action,
  runInAction,
} from "mobx";
import {
  type Person,
  type Username,
  type PersonId,
  type PersonName,
  type Email,
  type PersonAttrs,
} from "../definitions/Person";
import { type ExportOptions, type ExportFileType } from "../definitions/Search";
import { mkAlert } from "../contexts/Alert";
import { showToastWhilstPending } from "../../util/alerts";

export default class PersonModel implements Person {
  id: PersonId;
  username: Username;
  firstName: PersonName;
  lastName: PersonName;
  email: ?Email;
  hasPiRole: boolean;
  hasSysAdminRole: boolean;
  workbenchId: WorkbenchId;
  bench: ?Container;
  _links: Array<_LINK>;
  isOperated: boolean = false;
  processingUserActions: boolean = false;

  constructor(attrs: PersonAttrs) {
    makeObservable(this, {
      id: observable,
      processingUserActions: observable,
      username: observable,
      firstName: observable,
      lastName: observable,
      hasPiRole: observable,
      hasSysAdminRole: observable,
      email: observable,
      workbenchId: observable,
      bench: observable,
      isOperated: observable,
      setProcessingUserActions: action,
      getBench: action,
      exportRecords: action,
      exportData: action,
      isCurrentUser: computed,
      label: computed,
      firstInitial: computed,
      fullName: computed,
      groupByLabel: computed,
    });
    this.id = attrs.id;
    this.username = attrs.username;
    this.email = attrs.email;
    this.firstName = attrs.firstName;
    this.lastName = attrs.lastName;
    this.hasPiRole = attrs.hasPiRole;
    this.hasSysAdminRole = attrs.hasSysAdminRole;
    this.workbenchId = attrs.workbenchId;
    this.bench = attrs.bench ?? null;
    this._links = attrs._links;
  }

  get isCurrentUser(): boolean {
    const currentUser = getRootStore().peopleStore.currentUser;
    if (!currentUser) throw new Error("Current user is not yet known.");
    return currentUser.username === this.username;
  }

  get label(): string {
    return `${this.firstName} ${this.lastName} (${this.username})`;
  }

  get firstInitial(): string {
    return this.firstName.toUpperCase()[0];
  }

  get fullName(): PersonName {
    return `${this.firstName} ${this.lastName}`;
  }

  get groupByLabel(): string {
    if (this.isCurrentUser) return "me";
    return this.firstInitial;
  }

  setProcessingUserActions(value: boolean): void {
    this.processingUserActions = value;
  }

  async getBench(): Promise<Container> {
    if (!this.workbenchId)
      throw new Error("This user doesn't have a Workbench ID");
    const bench = await getRootStore().searchStore.getBench(this.workbenchId);
    runInAction(() => {
      this.bench = bench;
    });
    return bench;
  }

  async exportRecords(
    exportOptions: ExportOptions,
    username: Username
  ): Promise<void> {
    this.setProcessingUserActions(true);
    const { uiStore, trackingStore } = getRootStore();
    try {
      const { exportMode, includeContainerContent, resultFileType } =
        exportOptions;
      const params = new FormData();
      params.append(
        "exportSettings",
        JSON.stringify({
          users: [username],
          exportMode,
          // if omitted, ZIP is assumed
          ...((resultFileType === null ? {} : { resultFileType }): {|
            resultFileType?: ExportFileType,
          |}),
          ...((includeContainerContent === null
            ? {}
            : {
                includeContainerContent: includeContainerContent === "INCLUDE",
              }): {| includeContainerContent?: boolean |}),
        })
      );

      const { data } = await showToastWhilstPending(
        "Exporting User Data...",
        ApiService.post<
          typeof params,
          { _links: Array<{ link: string, rel: string }> }
        >("export", params)
      );
      const downloadLink = data._links[1];
      const fileName = downloadLink.link.split("downloadArchive/")[1];
      // create link for download
      const link = document.createElement("a");
      link.setAttribute("href", downloadLink.link);
      link.setAttribute("rel", downloadLink.rel);
      link.setAttribute("download", fileName);
      link.click(); // trigger download
      trackingStore.trackEvent("user:export:allTheirItems:Inventory", exportOptions);
    } catch (error) {
      uiStore.addAlert(
        mkAlert({
          title: `Data export failed.`,
          message:
            error.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error(`Could not export user's data.`, error);
      throw error;
    } finally {
      this.setProcessingUserActions(false);
    }
  }

  exportData(exportOptions: ExportOptions, username: Username): Promise<void> {
    return this.exportRecords(exportOptions, username);
  }
}

type SortOptions = {
  /*
   * Ignores lexicographical sorting for the current user if true and places
   * them always first. Will throw error if current user is not yet known.
   */
  placeCurrentFirst?: boolean,
};

export const sortPeople = (
  people: Array<PersonModel>,
  options: SortOptions = {}
): Array<PersonModel> => {
  const { placeCurrentFirst = false } = options;

  return people.sort((u1, u2) => {
    if (placeCurrentFirst) {
      if (u1.isCurrentUser) return -1;
      if (u2.isCurrentUser) return 1;
    }
    return u1.fullName.localeCompare(u2.fullName);
  });
};
