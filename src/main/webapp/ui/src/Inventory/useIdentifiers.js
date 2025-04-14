// @flow

import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../common/useOauthToken";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import * as Parsers from "../util/parsers";
import Result from "../util/result";

export type Identifier = {|
  id: number,
  doiType: string,
  doi: string,
  associatedGlobalId: string | null,
  creatorName: string,
  state: string,
|};

/**
 * Custom hook for working with the /identifiers endpoints
 */
export function useIdentifiers({
  state,
  isAssociated,
}: {|
  state?: "draft" | "findable" | "registered" | null,
  isAssociated?: boolean | null,
|}): {|
  identifiers: $ReadOnlyArray<Identifier>,
  loading: boolean,
  error: Error | null,
  bulkRegister: ({| count: number |}) => Promise<void>,
  deleteIdentifiers: ($ReadOnlyArray<Identifier>) => Promise<void>,
|} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [identifiers, setIdentifiers] = React.useState<
    $ReadOnlyArray<Identifier>
  >([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<Error | null>(null);

  const fetchIdentifiers = async () => {
    setLoading(true);
    try {
      const token = await getToken();
      const searchParams = new URLSearchParams();
      if (state) {
        searchParams.append("state", state);
      }
      if (typeof isAssociated !== "undefined" && isAssociated !== null) {
        searchParams.append("isAssociated", isAssociated ? "true" : "false");
      }
      const response = await axios.get<mixed>("/api/inventory/v1/identifiers", {
        headers: {
          Authorization: `Bearer ${token}`,
        },
        params: searchParams,
      });
      const parsedIdentifiers = Parsers.isArray(response.data)
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
                      "associatedGlobalId"
                    )(data)
                      .flatMap<string | null>((gId) =>
                        Parsers.isString(gId).orElseTry(() =>
                          Parsers.isNull(gId)
                        )
                      )
                      .elseThrow();

                    const creatorName = Parsers.getValueWithKey("creatorName")(
                      data
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
                    return Result.Error<Identifier>([e]);
                  }
                })
            )
          )
        )
        .orElseGet<$ReadOnlyArray<Identifier>>(([e]) => {
          setError(
            new Error("Failed to parse identifiers", {
              cause: e,
            })
          );
          addAlert(
            mkAlert({
              variant: "error",
              title: "Error parsing identifiers",
              message: "Please try refreshing.",
            })
          );
          return [];
        });
      setIdentifiers(parsedIdentifiers);
    } catch (e) {
      setError(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error fetching identifiers",
          message: e.message,
        })
      );
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    void fetchIdentifiers();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - addAlert wont meaningfully change between renders
     * - getToken wont meaningfully change between renders
     */
  }, [state, isAssociated]);

  async function bulkRegister({ count }: {| count: number |}) {
    try {
      const token = await getToken();
      const response = await axios.post<mixed, mixed>(
        `/api/inventory/v1/identifiers/bulk/${count}`,
        {},
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: `Successfully registered ${count} identifiers`,
        })
      );
      void fetchIdentifiers();
    } catch (e) {
      setError(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error registering identifiers",
          message: e.response.data.message ?? e.message,
        })
      );
    }
  }

  async function deleteIdentifiers(identifiers: $ReadOnlyArray<Identifier>) {
    try {
      const token = await getToken();
      const failed: Array<Identifier["doi"]> = [];
      const success: Array<Identifier["doi"]> = [];
      for (const identifier of identifiers) {
        try {
          await axios.delete<mixed>(
            `/api/inventory/v1/identifiers/${identifier.id}`,
            {
              headers: {
                Authorization: `Bearer ${token}`,
              },
            }
          );
          success.push(identifier.doi);
        } catch (e) {
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
          })
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
          })
        );
      }
      void fetchIdentifiers();
    } catch (e) {
      setError(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error deleting identifiers",
          message: e.message,
        })
      );
    }
  }

  return { identifiers, loading, error, bulkRegister, deleteIdentifiers };
}
