//@flow

import React from "react";
import * as FetchingData from "../../util/fetchingData";
import axios from "axios";
import useOauthToken from "../../common/useOauthToken";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";

type UiNavigationData = {|
  userDetails: {|
    email: string,
  |},
|};

/**
 * This hook fetches the state required to display the conditional parts of
 * the AppBar.
 */
export default function useUiNavigationData(): FetchingData.Fetched<UiNavigationData> {
  const { getToken } = useOauthToken();
  const [loading, setLoading] = React.useState(true);
  const [uiData, setUiData] = React.useState<null | UiNavigationData>(null);
  const [errorMessage, setErrrorMessage] = React.useState<null | string>(null);

  async function getUiNavigationData(): Promise<void> {
    setUiData(null);
    setLoading(true);
    setErrrorMessage(null);
    try {
      const token = await getToken();
      const { data } = await axios.get<mixed>(
        "/api/v1/userDetails/uiNavigationData",
        {
          headers: {
            Authorization: "Bearer " + token,
          },
        }
      );
      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .flatMap((obj) => {
          try {
            const email = Parsers.objectPath(["userDetails", "email"], obj)
              .flatMap(Parsers.isString)
              .elseThrow();
            return Result.Ok({ userDetails: { email } });
          } catch (e) {
            return Result.Error<UiNavigationData>([
              new Error("Could not parse response from /uiNavigationData", {
                cause: e,
              }),
            ]);
          }
        })
        .mapError(([e]) => {
          throw e;
        })
        .do((newUiData) => {
          setUiData(newUiData);
        });
    } catch (e) {
      console.error(e);
      setErrrorMessage(e.message);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    void getUiNavigationData();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getUiNavigationData will not meaningfully change
     */
  }, []);

  if (loading) return { tag: "loading" };
  if (errorMessage) return { tag: "error", error: errorMessage };
  if (!uiData)
    return {
      tag: "error",
      error: "useUiNavigationData is in an invalid state",
    };
  return { tag: "success", value: uiData };
}
