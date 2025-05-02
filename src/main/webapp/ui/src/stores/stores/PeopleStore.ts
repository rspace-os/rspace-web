import ElnApiService from "../../common/ElnApiService";
import InvApiService from "../../common/InvApiService";
import RsSet from "../../util/set";
import { type WorkbenchId } from "../definitions/Container";
import { type Username, type PersonAttrs } from "../definitions/Person";
import ContainerModel, { type ContainerAttrs } from "../models/ContainerModel";
import PersonModel from "../models/PersonModel";
import { type RootStore } from "./RootStore";
import MemoisedFactory from "../models/Factory/MemoisedFactory";
import { observable, makeObservable, runInAction, action } from "mobx";
import { type Group } from "../definitions/Group";

export default class PeopleStore {
  rootStore: RootStore;
  currentUser: ?PersonModel = null;
  groupMembers: ?RsSet<PersonModel>;
  currentUsersGroups: ?Promise<Array<Group>>;

  constructor(rootStore: RootStore) {
    this.rootStore = rootStore;
    this.groupMembers = null;
    this.currentUsersGroups = null;
    makeObservable(this, {
      currentUser: observable,
      groupMembers: observable,
      currentUsersGroups: observable,
      fetchCurrentUsersGroups: action,
    });
  }

  async fetchCurrentUser(): Promise<?PersonModel> {
    try {
      const [user, operated] = await Promise.all([
        ElnApiService.get<void, PersonAttrs>("userDetails/whoami"),
        ElnApiService.get<void, boolean>("userDetails/isOperatedAs"),
      ]);
      const currentUser = new MemoisedFactory().newPerson(user.data);
      runInAction(() => {
        this.currentUser = currentUser;
        this.currentUser.isOperated = operated.data;
      });
      return currentUser;
    } catch (e) {
      console.error("Could not get current user", e);
    }
  }

  /**
   * All of the users that are in any of the lab groups that the
   * current user is a member of, including the current user.
   */
  async fetchMembersOfSameGroup(): Promise<RsSet<PersonModel>> {
    if (this.groupMembers) return this.groupMembers;
    try {
      const { data } = await ElnApiService.get<
        void,
        Array<PersonAttrs> | {| data: null, message: string |}
      >("userDetails/groupMembers");
      if (this.currentUser === null)
        throw new Error("Current user is not known");
      if (Array.isArray(data)) {
        const factory = new MemoisedFactory();
        const members = new RsSet(data.map((d) => factory.newPerson(d)));
        runInAction(() => {
          this.groupMembers = members;
        });
        return members;
      }
      throw new Error(data.message);
    } catch (e) {
      console.error(
        "Could not fetch set of users in the same group as current user",
        e
      );
      throw e;
    }
  }

  /*
   * The set of users whose names match the search term, including current user.
   *  Search term length check mimics limits of API.
   */
  async searchPeople(searchTerm: string): Promise<RsSet<PersonModel>> {
    if (searchTerm.length < 3)
      throw new Error("Search string must be at least 3 characters long.");
    try {
      const { data } = await ElnApiService.get<void, Array<PersonAttrs>>(
        "",
        `userDetails/search?query=${searchTerm}`
      );
      const factory = new MemoisedFactory();
      return new RsSet(data.map((d) => factory.newPerson(d)));
    } catch (e) {
      console.error("Could not search for users", e);
      throw e;
    }
  }

  /*
   * The set of groups whose names match the search term.
   *  Search term length check mimics limits of API.
   */
  async searchGroups(searchTerm: string): Promise<RsSet<Group>> {
    if (searchTerm.length < 3)
      throw new Error("Search string must be at least 3 characters long.");
    try {
      const { data } = await ElnApiService.get<void, Array<Group>>(
        "",
        `groups/search?query=${searchTerm}`
      );
      return new RsSet(data);
    } catch (e) {
      console.error("Could not search for users", e);
      throw e;
    }
  }

  async getUser(username: Username): Promise<?PersonModel> {
    return [...(await this.searchPeople(username))].find(
      (u) => u.username === username
    );
  }

  async getPersonFromBenchId(id: WorkbenchId): Promise<PersonModel> {
    const { data } = await InvApiService.get<void, ContainerAttrs>(
      "workbenches",
      id
    );
    const factory = new MemoisedFactory();
    if (!data.owner) throw new Error("This bench doesn't have an owner");
    const person = factory.newPerson({
      ...data.owner,
      workbenchId: id,
      bench: new ContainerModel(new MemoisedFactory(), data),
    });
    return person;
  }

  fetchCurrentUsersGroups(): Promise<Array<Group>> {
    if (!this.currentUsersGroups) {
      this.currentUsersGroups = ElnApiService.get<void, Array<Group>>(
        "groups"
      ).then(({ data }) => data);
    }
    return this.currentUsersGroups;
  }
}
