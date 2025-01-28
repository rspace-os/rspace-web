//@flow

import React from "react";
import * as FetchingData from "../../util/fetchingData";
import axios from "axios";
import useOauthToken from "../../common/useOauthToken";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";

/**
 * The state requried to display the conditional parts of the AppBar.
 */
export type UiNavigationData = {|
  userDetails: {|
    email: string,
    orcidId: null | string,
    fullName: string,
    username: string,
    profileImgSrc: null | string,
  |},
  visibleTabs: {|
    published: boolean,
    inventory: boolean,
    system: boolean,
    myLabGroups: boolean,
  |},
  bannerImgSrc: string,
  operatedAs: boolean,
  nextMaintenance: null | { ... },
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
            const orcidId = Parsers.objectPath(["userDetails", "orcidId"], obj)
              .flatMap((o) =>
                Parsers.isString(o).orElseTry(() => Parsers.isNull(o))
              )
              .elseThrow();
            const fullName = Parsers.objectPath(
              ["userDetails", "fullName"],
              obj
            )
              .flatMap(Parsers.isString)
              .elseThrow();
            const username = Parsers.objectPath(
              ["userDetails", "username"],
              obj
            )
              .flatMap(Parsers.isString)
              .elseThrow();
            const profileImgSrc = Parsers.objectPath(
              ["userDetails", "profileImgSrc"],
              obj
            )
              .flatMap((o) =>
                Parsers.isString(o).orElseTry(() => Parsers.isNull(o))
              )
              .elseThrow();
            const published = Parsers.objectPath(
              ["visibleTabs", "published"],
              obj
            )
              .flatMap(Parsers.isBoolean)
              .elseThrow();
            const inventory = Parsers.objectPath(
              ["visibleTabs", "inventory"],
              obj
            )
              .flatMap(Parsers.isBoolean)
              .elseThrow();
            const system = Parsers.objectPath(["visibleTabs", "system"], obj)
              .flatMap(Parsers.isBoolean)
              .elseThrow();
            const myLabGroups = Parsers.objectPath(
              ["visibleTabs", "myLabGroups"],
              obj
            )
              .flatMap(Parsers.isBoolean)
              .elseThrow();
            const bannerImgSrc = Parsers.objectPath(["bannerImgSrc"], obj)
              .flatMap(Parsers.isString)
              .elseThrow();
            const operatedAs = Parsers.objectPath(["operatedAs"], obj)
              .flatMap(Parsers.isBoolean)
              .elseThrow();
            const nextMaintenance = Parsers.objectPath(["nextMaintenance"], obj)
              .flatMap((o) =>
                Parsers.isObject(o).orElseTry(() => Parsers.isNull(o))
              )
              .elseThrow();
            return Result.Ok({
              userDetails: {
                email,
                orcidId,
                fullName,
                username,
                profileImgSrc,
              },
              visibleTabs: {
                published,
                inventory,
                system,
                myLabGroups,
              },
              bannerImgSrc,
              operatedAs,
              nextMaintenance,
            });
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
