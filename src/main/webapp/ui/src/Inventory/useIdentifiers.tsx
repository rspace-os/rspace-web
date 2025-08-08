import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../hooks/api/useOauthToken";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import * as Parsers from "../util/parsers";
import Result from "../util/result";
import { type InventoryRecord } from "../stores/definitions/InventoryRecord";

/**
 * The definition of an identifier, as returned by the API. Do note that this
 * may be different to how identifiers are defined in the rest of the system.
 */
export type Identifier = {
  id: number;
  doiType: string;
  doi: string;
  associatedGlobalId: string | null;
  creatorName: string;
  state: string;
};

function getErrorMessage(error: unknown): Result<string> {
  return Parsers.objectPath(["response", "data", "message"], error)
    .flatMap(Parsers.isString)
    .orElseTry(() =>
      Parsers.isObject(error).flatMap((e) =>
        e instanceof Error
          ? Result.Ok(e.message)
          : Result.Error([new Error("Unknown error")]),
      ),
    );
}

/**
 * Custom hook for working with the /identifiers endpoints
 */
export function useIdentifiers(): {
  /*
   * Make a GET request to /identifiers to fetch a list of identifiers.
   */
  getIdentifiers: ({
    state,
    isAssociated,
    searchTerm,
  }: {
    state?: "draft" | "findable" | "registered" | null;
    isAssociated?: boolean | null;
    searchTerm?: string;
  }) => Promise<ReadonlyArray<Identifier>>;
  /*
   * Make a POST request to /identifiers/bulk/{count} to register a number of
   * identifiers.
   */
  bulkRegister: ({ count }: { count: number }) => Promise<void>;
  /*
   * Make a DELETE request to /identifiers/{id} to delete a number of identifiers.
   */
  deleteIdentifiers: (identifiers: Set<Identifier>) => Promise<void>;
  /*
   * Make a POST request to /identifiers/{id}/assign to assign an unassigned
   * identifier to an existing Inventory record.
   */
  assignIdentifier: (
    identifier: Identifier,
    record: InventoryRecord,
  ) => Promise<void>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  const getIdentifiers = async ({
    state,
    isAssociated,
    searchTerm,
  }: {
    state?: "draft" | "findable" | "registered" | null;
    isAssociated?: boolean | null;
    searchTerm?: string;
  }) => {
    try {
      const token = await getToken();
      const searchParams = new URLSearchParams();
      if (state) {
        searchParams.append("state", state);
      }
      if (typeof isAssociated !== "undefined" && isAssociated !== null) {
        searchParams.append("isAssociated", isAssociated ? "true" : "false");
      }
      if (searchTerm) {
        searchParams.append("identifier", searchTerm);
      }
      const response = await axios.get<unknown>(
        "/api/inventory/v1/identifiers",
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          params: searchParams,
        },
      );
      return Parsers.isArray(response.data)
        .flatMap((array) =>
          Result.all(
            ...array.map((obj) =>
              Parsers.isObject(obj)
                .flatMap(Parsers.isNotNull)
                .flatMap((data) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(data)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();

                    const doiType = Parsers.getValueWithKey("doiType")(data)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const doi = Parsers.getValueWithKey("doi")(data)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const associatedGlobalId = Parsers.getValueWithKey(
                      "associatedGlobalId",
                    )(data)
                      .flatMap<string | null>((gId) =>
                        Parsers.isString(gId).orElseTry(() =>
                          Parsers.isNull(gId),
                        ),
                      )
                      .elseThrow();

                    const creatorName = Parsers.getValueWithKey("creatorName")(
                      data,
                    )
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    const state = Parsers.getValueWithKey("state")(data)
                      .flatMap(Parsers.isString)
                      .elseThrow();

                    return Result.Ok({
                      id,
                      doiType,
                      doi,
                      associatedGlobalId,
                      creatorName,
                      state,
                    });
                  } catch (e) {
                    console.error(e);
                    if (!(e instanceof Error)) {
                      return Result.Error<Identifier>([
                        new Error("Unknown error"),
                      ]);
                    }
                    return Result.Error<Identifier>([e]);
                  }
                }),
            ),
          ),
        )
        .elseThrow();
    } catch (e) {
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error fetching identifiers",
            message: getErrorMessage(e).elseThrow(),
          }),
        );
        throw e;
      }
      throw new Error("Unexpected error");
    }
  };

  async function bulkRegister({ count }: { count: number }) {
    try {
      const token = await getToken();
      await axios.post<unknown>(
        `/api/inventory/v1/identifiers/bulk/${count}`,
        {},
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: `Successfully registered ${count} identifiers`,
        }),
      );
    } catch (e) {
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error registering identifiers",
            message: getErrorMessage(e).elseThrow(),
          }),
        );
        throw e;
      }
    }
  }

  async function deleteIdentifiers(identifiers: Set<Identifier>) {
    try {
      const token = await getToken();
      const failed: Array<Identifier["doi"]> = [];
      const success: Array<Identifier["doi"]> = [];
      for (const identifier of identifiers) {
        try {
          await axios.delete<unknown>(
            `/api/inventory/v1/identifiers/${identifier.id}`,
            {
              headers: {
                Authorization: `Bearer ${token}`,
              },
            },
          );
          success.push(identifier.doi);
        } catch {
          failed.push(identifier.doi);
        }
      }
      if (failed.length > 0) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error deleting identifiers",
            message: "Failed to delete some of the identifiers.",
            details: failed.map((doi) => ({
              title: `Failed to delete "${doi}"`,
              variant: "error",
            })),
          }),
        );
      }
      if (success.length > 0) {
        addAlert(
          mkAlert({
            variant: "success",
            message: "Successfully deleted the identifiers.",
            details: success.map((doi) => ({
              title: `Deleted "${doi}"`,
              variant: "success",
            })),
          }),
        );
      }
    } catch (e) {
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error deleting identifiers",
            message: e.message,
          }),
        );
        throw e;
      }
    }
  }

  async function assignIdentifier(
    identifier: Identifier,
    record: InventoryRecord,
  ) {
    try {
      const token = await getToken();
      await axios.post<unknown>(
        `/api/inventory/v1/identifiers/${identifier.id}/assign`,
        { parentGlobalId: record.globalId },
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: `Successfully assigned ${identifier.doi} to ${record.globalId}`,
        }),
      );
    } catch (e) {
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            variant: "error",
            title: "Error assigning identifier",
            message: getErrorMessage(e).elseThrow(),
          }),
        );
        throw e;
      }
    }
  }

  return { getIdentifiers, bulkRegister, deleteIdentifiers, assignIdentifier };
}

/**
 * Abstraction over useIdentifiers to provide a simple interface for
 * fetching a listing of identifiers. Whenever the parameters change,
 * the identifiers will be fetched again.
 */
export function useIdentifiersListing({
  state,
  isAssociated,
  searchTerm,
}: {
  state?: "draft" | "findable" | "registered" | null;
  isAssociated?: boolean | null;
  searchTerm?: string;
}): {
  /*
   * The fetchede identifiers. When loading is true or error is not null, this
   * array will contain the last successfully fetched listing.
   */
  identifiers: ReadonlyArray<Identifier>;

  /*
   * Manually refresh the listing.
   */
  refreshListing: () => Promise<void>;

  loading: boolean;
  error: Error | null;
} {
  const { getIdentifiers } = useIdentifiers();
  const [identifiers, setIdentifiers] = React.useState<
    ReadonlyArray<Identifier>
  >([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<Error | null>(null);

  const fetchIdentifiers = React.useCallback(async () => {
    try {
      setLoading(true);
      setIdentifiers(await getIdentifiers({ state, isAssociated, searchTerm }));
    } catch (e) {
      if (e instanceof Error) {
        setError(e);
      }
    } finally {
      setLoading(false);
    }
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getIdentifiers wont meaningfully change between renders
     */
  }, [state, isAssociated, searchTerm]);

  React.useEffect(() => {
    void fetchIdentifiers();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getIdentifiers wont meaningfully change between renders
     */
  }, [state, isAssociated, searchTerm]);

  return { identifiers, loading, error, refreshListing: fetchIdentifiers };
}

const IdentifiersRefreshContext = React.createContext<{
  refreshListing: (() => Promise<void>) | null;
  setRefreshListing: (fn: (() => Promise<void>) | null) => void;
}>({
  refreshListing: null,
  setRefreshListing: () => {},
});

/**
 * Provider component for the refreshListing function
 */
export function IdentifiersRefreshProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [refreshListing, setRefreshListing] = React.useState<
    (() => Promise<void>) | null
  >(null);

  return (
    <IdentifiersRefreshContext.Provider
      value={{
        refreshListing,
        setRefreshListing: (f) => setRefreshListing(() => f),
      }}
    >
      {children}
    </IdentifiersRefreshContext.Provider>
  );
}

/**
 * Hook to access and set the refreshListing function
 */
export function useIdentifiersRefresh() {
  return React.useContext(IdentifiersRefreshContext);
}
