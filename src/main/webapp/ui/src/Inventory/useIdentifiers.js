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
  associatedGlobalId: string,
  creatorName: string,
  state: string,
|};

/**
 * Custom hook for working with the /identifiers endpoints
 */
export const useIdentifiers = ({
  state,
}: {|
  state?: "draft" | "findable" | "registered" | null,
|}): {|
  identifiers: $ReadOnlyArray<Identifier>,
  loading: boolean,
  error: Error | null,
|} => {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [identifiers, setIdentifiers] = React.useState<
    $ReadOnlyArray<Identifier>
  >([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<Error | null>(null);

  React.useEffect(() => {
    const fetchIdentifiers = async () => {
      setLoading(true);
      try {
        const token = await getToken();
        const searchParams = new URLSearchParams();
        if (state) {
          searchParams.append("state", state);
        }
        searchParams.append("isAssociated", "true");
        const response = await axios.get<mixed>(
          "/api/inventory/v1/identifiers",
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
            params: searchParams,
          }
        );
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
                        .flatMap(Parsers.isString)
                        .elseThrow();

                      const creatorName = Parsers.getValueWithKey(
                        "creatorName"
                      )(data)
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

    void fetchIdentifiers();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - addAlert wont meaningfully change between renders
     * - getToken wont meaningfully change between renders
     */
  }, [state]);

  return { identifiers, loading, error };
};
