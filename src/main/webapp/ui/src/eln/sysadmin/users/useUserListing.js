//@flow

import React from "react";
import * as FetchingData from "../../../util/fetchingData";
import axios from "axios";
import { Optional } from "../../../util/optional";
import Result from "../../../util/result";
import RsSet from "../../../util/set";
import * as Parsers from "../../../util/parsers";

export opaque type UserId = number;

type FetchedUser = {|
  userInfo: {|
    id: UserId,
    fullName: string,
    firstName: string,
    lastName: string,
    email: string,
    username: string,
    role: string,
    enabled: boolean,
    accountLocked: boolean,
    groupNames: Array<string>,
    tags: Array<string>,
    usernameAlias: string,
  |},
  recordCount: number,
  fileUsage: number,
  lastLogin: number,
  creationDate: number,
  hasFormsUsedByOtherUsers: boolean,
|};

type FetchedData = {|
  userStats: {|
    availableSeats: string,
    usedLicenseSeats: number,
    totalEnabledSysAdmins: number,
    totalEnabledRSpaceAdmins: number,
    totalUsers: number,
  |},
  userInfo: {|
    results: Array<FetchedUser>,
    totalHits: number,
    pageNumber: number,
    hitsPerPage: number,
  |},
  pgCrit: {|
    orderBy: string,
    sortOrder: "ASC" | "DESC",
    searchCriteria: {|
      allFields: string,
    |},
  |},
|};

export opaque type ApiParameters = {|
  page: number,
  pageSize: number,
  orderBy: string | null,
  sortOrder: "asc" | "desc" | null,
  searchTerm: string,
  tags: Array<string>,
|};

export type User = {|
  id: UserId,
  url: string,
  fullName: string,
  firstName: string,
  lastName: string,
  email: string,
  username: string,
  role: string,
  isPi: boolean,
  isRegularUser: boolean,
  recordCount: number,
  fileUsage: number,
  lastLogin: Optional<Date>,
  created: Optional<Date>,
  enabled: boolean,
  locked: boolean,
  groups: Array<string>,
  tags: Array<string>,
  usernameAlias: string,
  grantPiRole: (string) => Promise<void>,
  revokePiRole: (string) => Promise<void>,
  unlock: () => Promise<void>,
  enable: () => Promise<void>,
  disable: () => Promise<void>,
  delete: () => Promise<void>,
  setAlias: (string) => Promise<void>,
  hasFormsUsedByOtherUsers: boolean,
|};

export type UserListing = {|
  users: Array<User>,
  totalListingCount: number,
  getById: (UserId) => Result<User>,

  // search parameters
  getSearchParameters: () => ApiParameters,
  setSearchParameters: (ApiParameters) => Promise<void>,
  page: number,
  setPage: (number) => Promise<void>,
  pageSize: number,
  setPageSize: (number) => Promise<void>,
  orderBy: string | null,
  sortOrder: "asc" | "desc" | null,
  setOrdering: (string, "asc" | "desc") => Promise<void>,
  clearOrdering: () => Promise<void>,
  searchTerm: string,
  setSearchTerm: (string) => Promise<void>,
  tags: Array<string>,
  applyTagsFilter: (Array<string>) => Promise<void>,
  allUsers: () => Promise<UserListing>,

  // summary info
  availableSeats: string,
  billableUsersCount: number,
  systemAdminCount: number,
  communityAdminCount: number,
  totalUsersCount: number,

  // operations on multiple users
  setTags: (
    users: $ReadOnlyArray<User>,
    addedTags: $ReadOnlyArray<string>,
    deletedTags: $ReadOnlyArray<string>
  ) => Promise<void>,
|};

export function useUserListing(): {|
  userListing: FetchingData.Fetched<UserListing>,
|} {
  const [userListing, setUserListing] = React.useState<
    FetchingData.Fetched<UserListing>
  >({ tag: "loading" });

  async function listUsers(params: ApiParameters): Promise<void> {
    const { page, pageSize, orderBy, sortOrder, searchTerm, tags } = params;
    setUserListing({ tag: "loading" });
    try {
      const { data } = await axios.get<
        | FetchedData
        | {|
            exceptionMessage: string,
          |}
      >(`system/ajax/jsonList`, {
        params: new URLSearchParams([
          ["pageNumber", String(page)],
          ["resultsPerPage", String(pageSize)],
          ...(orderBy ? [["orderBy", orderBy]] : []),
          ...(() => {
            if (sortOrder === "desc") return [["sortOrder", "DESC"]];
            if (sortOrder === "asc") return [["sortOrder", "ASC"]];
            return [];
          })(),
          ...(searchTerm ? [["allFields", searchTerm]] : []),
          ...tags.map((t) => ["tags[]", t]),
        ]),
      });
      if (typeof data.exceptionMessage !== "undefined") {
        throw new Error(data.exceptionMessage);
      } else {
        setUserListing({
          tag: "success",
          // $FlowExpectedError[prop-missing]
          value: listingConstructor(params, data),
        });
      }
    } catch (error) {
      console.error(error);
      setUserListing({ tag: "error", error: error.message });
    }
  }

  const listingConstructor = (
    apiParameters: ApiParameters,
    fetchedData: FetchedData
  ): UserListing => {
    const userConstructor = (fetchedUser: FetchedUser): User => {
      const id = fetchedUser.userInfo.id;
      const role = fetchedUser.userInfo.role;
      const isPi = role.includes("ROLE_PI");

      const refreshListing = () => {
        void listUsers(apiParameters);
      };

      async function grantPiRole(password: string): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          await axios.postForm<
            {| userId: UserId, sysadminPassword: string |},
            ""
          >("/system/ajax/grantPIRole", {
            userId: id,
            sysadminPassword: password,
          });
          refreshListing();
        } catch (error) {
          if (error.response?.data?.message) {
            const message = error.response.data.message;
            console.error(message);
            throw new Error(message);
          }
          console.error(error);
          throw error;
        }
      }

      async function revokePiRole(password: string): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          await axios.postForm<
            {| userId: UserId, sysadminPassword: string |},
            ""
          >("/system/ajax/revokePIRole", {
            userId: id,
            sysadminPassword: password,
          });
          refreshListing();
        } catch (error) {
          if (error.response?.data?.message) {
            const message = error.response.data.message;
            console.error(message);
            throw new Error(message);
          }
          console.error(error);
          throw error;
        }
      }

      async function unlock(): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          const { data } = await axios.postForm<
            {| userId: UserId |},
            | {||}
            | {|
                exceptionMessage: string,
              |}
          >("/system/ajax/unlockAccount", {
            userId: id,
          });
          if (typeof data.exceptionMessage !== "undefined") {
            // note that this exceptionMessage seems to always just be "Database exception - query could not be executed."
            throw new Error(data.exceptionMessage);
          } else {
            refreshListing();
          }
        } catch (error) {
          console.error(error);
          throw error;
        }
      }

      async function enable(): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          const { data } = await axios.postForm<
            {| userId: UserId |},
            | {||}
            | {|
                exceptionMessage: string,
              |}
          >("/system/ajax/setAccountEnablement", {
            userId: id,
            enabled: true,
          });
          if (typeof data.exceptionMessage !== "undefined") {
            // note that this exceptionMessage seems to always just be "Database exception - query could not be executed."
            throw new Error(data.exceptionMessage);
          } else {
            refreshListing();
          }
        } catch (error) {
          console.error(error);
          throw error;
        }
      }

      async function disable(): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          const { data } = await axios.postForm<
            {| userId: UserId |},
            | {||}
            | {|
                exceptionMessage: string,
              |}
          >("/system/ajax/setAccountEnablement", {
            userId: id,
            enabled: false,
          });
          if (typeof data.exceptionMessage !== "undefined") {
            // note that this exceptionMessage seems to always just be "Database exception - query could not be executed."
            throw new Error(data.exceptionMessage);
          } else {
            refreshListing();
          }
        } catch (error) {
          console.error(error);
          throw error;
        }
      }

      async function deleteUser(): Promise<void> {
        try {
          // $FlowExpectedError[prop-missing] Type definition doesn't know about postForm
          const { data } = await axios.postForm<
            {| userId: UserId |},
            | {||}
            | {|
                error: {| errorMessages: Array<string> |},
              |}
          >("/system/ajax/removeUserAccount/", {
            userId: id,
          });
          Parsers.isObject(data)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("exceptionMessage"))
            .flatMap(Parsers.isString)
            .do((exceptionMessage) => {
              throw new Error(exceptionMessage);
            });
          refreshListing();
        } catch (error) {
          if (error.response?.data?.message) {
            const message = error.response.data.message;
            console.error(message);
            throw new Error(message);
          }
          console.error(error);
          throw error;
        }
      }

      async function setAlias(alias: string): Promise<void> {
        try {
          const { data } = await axios.post<
            {| userId: UserId, usernameAlias: string |},
            mixed
          >("/system/users/saveUsernameAlias", {
            userId: id,
            usernameAlias: alias,
          });
          Parsers.isObject(data)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("exceptionMessage"))
            .flatMap(Parsers.isString)
            .do((exceptionMessage) => {
              throw new Error(exceptionMessage);
            });

          refreshListing();
        } catch (error) {
          console.error(error);
          Parsers.objectPath(["response", "data", "message"], error)
            .flatMap(Parsers.isString)
            .do((message) => {
              throw new Error(message);
            });
          throw error;
        }
      }

      return {
        id,
        url: `/userform?userId=${id}`,
        fullName: fetchedUser.userInfo.fullName,
        firstName: fetchedUser.userInfo.firstName,
        lastName: fetchedUser.userInfo.lastName,
        email: fetchedUser.userInfo.email,
        username: fetchedUser.userInfo.username,
        role,
        isPi,
        isRegularUser: role.includes("ROLE_USER") && !isPi,
        recordCount: fetchedUser.recordCount,
        fileUsage: fetchedUser.fileUsage,
        lastLogin: Optional.fromNullable(fetchedUser.lastLogin).map(
          (l) => new Date(l)
        ),
        created: Optional.fromNullable(fetchedUser.creationDate).map(
          (l) => new Date(l)
        ),
        enabled: fetchedUser.userInfo.enabled,
        locked: fetchedUser.userInfo.accountLocked,
        groups: fetchedUser.userInfo.groupNames,
        tags: fetchedUser.userInfo.tags,
        usernameAlias: fetchedUser.userInfo.usernameAlias,
        hasFormsUsedByOtherUsers: fetchedUser.hasFormsUsedByOtherUsers,


        // operations on a single user
        grantPiRole,
        revokePiRole,
        unlock,
        enable,
        disable,
        delete: deleteUser,
        setAlias,
      };
    };

    const users = fetchedData.userInfo.results.map((r) => userConstructor(r));
    const totalListingCount = fetchedData.userInfo.totalHits;
    const { page, pageSize, orderBy, sortOrder, searchTerm, tags } =
      apiParameters;
    const {
      availableSeats,
      usedLicenseSeats,
      totalEnabledSysAdmins,
      totalEnabledRSpaceAdmins,
      totalUsers,
    } = fetchedData.userStats;

    const getSearchParameters = () => apiParameters;

    const setSearchParameters = (
      newApiParameters: ApiParameters
    ): Promise<void> => listUsers(newApiParameters);

    const setPage = (newPage: number): Promise<void> =>
      listUsers({
        ...apiParameters,
        page: newPage,
      });

    const setPageSize = (newPageSize: number): Promise<void> =>
      listUsers({
        ...apiParameters,
        page: 0,
        pageSize: newPageSize,
      });

    const setOrdering = (
      newOrderBy: string,
      newSortOrder: "asc" | "desc"
    ): Promise<void> =>
      listUsers({
        ...apiParameters,
        page: 0,
        orderBy: newOrderBy,
        sortOrder: newSortOrder,
      });

    const clearOrdering = (): Promise<void> =>
      listUsers({
        ...apiParameters,
        page: 0,
        orderBy: "",
        sortOrder: null,
      });

    const setSearchTerm = (newSearchTerm: string) =>
      listUsers({
        ...apiParameters,
        page: 0,
        searchTerm: newSearchTerm,
      });

    const applyTagsFilter = (newTags: Array<string>) =>
      listUsers({
        ...apiParameters,
        page: 0,
        tags: newTags,
      });

    async function allUsers(): Promise<UserListing> {
      setUserListing({ tag: "loading" });
      try {
        const { data } = await axios.get<
          | FetchedData
          | {|
              exceptionMessage: string,
            |}
        >(`system/ajax/jsonList`, {
          params: new URLSearchParams([
            ["pageNumber", String(0)],
            ["resultsPerPage", String(totalListingCount)],
            ...(orderBy ? [["orderBy", orderBy]] : []),
            ...(() => {
              if (sortOrder === "desc") return [["sortOrder", "DESC"]];
              if (sortOrder === "asc") return [["sortOrder", "ASC"]];
              return [];
            })(),
            ...(searchTerm ? [["allFields", searchTerm]] : []),
            ...tags.map((t) => ["tags[]", t]),
          ]),
        });
        if (typeof data.exceptionMessage !== "undefined") {
          throw new Error(data.exceptionMessage);
        } else {
          const newListing = listingConstructor(
            {
              page: 0,
              pageSize: 1,
              orderBy,
              sortOrder,
              searchTerm,
              tags,
            },
            // $FlowExpectedError[prop-missing]
            data
          );
          setUserListing({
            tag: "success",
            value: newListing,
          });
          return newListing;
        }
      } catch (error) {
        console.error(error);
        throw error;
      }
    }

    function getById(id: UserId): Result<User> {
      return Result.fromNullable(
        users.find((u) => u.id === id),
        new Error(`Could not find User with id ${id}.`)
      );
    }

    async function setTags(
      usersToBeTagged: $ReadOnlyArray<User>,
      addedTags: $ReadOnlyArray<string>,
      deletedTags: $ReadOnlyArray<string>
    ): Promise<void> {
      try {
        const { data } = await axios.post<
          Array<{|
            userId: UserId,
            userTags: $ReadOnlyArray<string>,
          |}>,
          | ""
          | {|
              exceptionMessage: string,
            |}
        >(
          `system/users/saveTagsForUsers`,
          usersToBeTagged.map((user) => ({
            userId: user.id,
            userTags: new RsSet(user.tags)
              .union(new RsSet(addedTags))
              .subtract(new RsSet(deletedTags))
              .toArray(),
          }))
        );
        if (typeof data === "object") throw new Error(data.exceptionMessage);
        await listUsers(apiParameters);
      } catch (error) {
        console.error(error);
        throw error;
      }
    }

    return {
      users,
      totalListingCount,
      getById,

      // search parameters
      getSearchParameters,
      setSearchParameters,
      page,
      setPage,
      pageSize,
      setPageSize,
      orderBy,
      sortOrder,
      setOrdering,
      clearOrdering,
      searchTerm,
      setSearchTerm,
      tags,
      applyTagsFilter,
      allUsers,

      // summary info
      availableSeats,
      billableUsersCount: usedLicenseSeats,
      systemAdminCount: totalEnabledSysAdmins,
      communityAdminCount: totalEnabledRSpaceAdmins,
      totalUsersCount: totalUsers,

      // operations on multiple users
      setTags,
    };
  };

  React.useEffect(() => {
    void listUsers({
      page: 0,
      pageSize: 10,
      orderBy: null,
      sortOrder: null,
      searchTerm: "",
      tags: [],
    });
  }, []);

  return { userListing };
}
