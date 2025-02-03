//@flow

import axios from "axios";
import React from "react";
import Result from "../../../util/result";
import * as Parsers from "../../../util/parsers";
import * as FetchingData from "../../../util/fetchingData";
import useOauthToken from "../../../common/useOauthToken";
import { Optional } from "../../../util/optional";
import AlertContext, {
  mkAlert,
  type Alert,
} from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { stableSort } from "../../../util/table";

type Link = {| operation: string, href: string |};

function parseIrodsLocationLinks(obj: { ... }): Result<$ReadOnlyArray<Link>> {
  return Parsers.getValueWithKey("_links")(obj)
    .flatMap(Parsers.isArray)
    .flatMap((linksArray) =>
      Result.all(
        ...linksArray.map((m: mixed) =>
          Parsers.isObject(m)
            .flatMap(Parsers.isNotNull)
            .flatMap((linkObj) =>
              Result.lift2((operation: string, href: string) => ({
                operation,
                href,
              }))(
                Parsers.getValueWithKey("operation")(linkObj).flatMap(
                  Parsers.isString
                ),
                Parsers.getValueWithKey("link")(linkObj).flatMap(
                  Parsers.isString
                )
              )
            )
        )
      )
    );
}

/*
 * Instead of returning the string wrapped in a Result, this function returns
 * it wrapped in Optional because a location not having a particular operation
 * is not an error; that particular operation may simply not be permitted on
 * the selected files with regards to a location.
 */
const parseSpecificHref =
  (op: string) =>
  (links: $ReadOnlyArray<Link>): Optional<string> =>
    ArrayUtils.find(({ operation }) => operation === op, links).map(
      ({ href }) => href
    );

const parseOperationError = (error: mixed): Result<string> =>
  Parsers.objectPath(["response", "data", "errors"], error)
    .flatMap(Parsers.isArray)
    .flatMap(ArrayUtils.head)
    .flatMap(Parsers.isString)
    .map((errorMsg) => {
      if (/attempt to overwrite file/.test(errorMsg))
        return "Some or all of the files already exist";
      if (/AuthenticationException/.test(errorMsg))
        return "Authentication failed";
      if (/InvalidUserException/.test(errorMsg)) return "Authentication failed";
      if (/java.net.UnknownHostException/.test(errorMsg))
        return "Could not reach the iRODs server";
      return errorMsg;
    });

function handleErrors(response: mixed): Alert {
  const data = Parsers.objectPath(["data"], response)
    .flatMap(Parsers.isObject)
    .flatMap(Parsers.isNotNull);

  return data
    .flatMap(Parsers.getValueWithKey("numFilesInput"))
    .flatMap(Parsers.isNumber)
    .flatMap((numFilesInput) =>
      data
        .flatMap(Parsers.getValueWithKey("numFilesSucceed"))
        .flatMap(Parsers.isNumber)
        .flatMap((numFilesSucceed) => {
          if (numFilesInput === numFilesSucceed)
            return Result.Ok(
              mkAlert({
                variant: "success",
                message: "Successfully moved the files.",
              })
            );
          return data
            .flatMap(Parsers.getValueWithKey("fileInfoDetails"))
            .flatMap(Parsers.isArray)
            .flatMap((fileInfoDetails) =>
              Result.all(
                ...fileInfoDetails.map((d) =>
                  Parsers.isObject(d)
                    .flatMap(Parsers.isNotNull)
                    .flatMap((obj) => {
                      const succeeded = Parsers.getValueWithKey("succeeded")(
                        obj
                      )
                        .flatMap(Parsers.isBoolean)
                        .flatMap(Parsers.isTrue);

                      /*
                       * If "succeeded" is true, then just parse the filename
                       * and display a green alert
                       */
                      return succeeded
                        .flatMap(() =>
                          Parsers.getValueWithKey("fileName")(obj)
                            .flatMap(Parsers.isString)
                            .map((filename) => ({
                              variant: "success",
                              title: filename,
                            }))
                        )
                        .orElseTry(() =>
                          /*
                           * Otherwise parse filename and reason, and show a red
                           * alert
                           */
                          Result.lift2((filename: string, reason: string) => ({
                            variant: "error",
                            title: filename,
                            help: reason,
                          }))(
                            Parsers.getValueWithKey("fileName")(obj).flatMap(
                              Parsers.isString
                            ),
                            Parsers.getValueWithKey("reason")(obj).flatMap(
                              Parsers.isString
                            )
                          )
                        );
                    })
                )
              ).map((details) =>
                mkAlert({
                  variant: "warning",
                  message: "Moving some files failed.",
                  details,
                  isInfinite: true,
                })
              )
            );
        })
    )
    .orElse(
      mkAlert({
        variant: "error",
        message: "Could not parse response.",
      })
    );
}

/**
 * A folder on an iRODS system.
 */
export type IrodsLocation = {|
  id: number,
  name: string,
  path: string,
  copy: Optional<({| username: string, password: string |}) => Promise<void>>,
  move: Optional<({| username: string, password: string |}) => Promise<void>>,
|};

/**
 * A custom hook for interacting with the /api/v1/gallery/irods API endpoint.
 */
export default function useIrods(
  /**
   * A list of IDs of files in the Gallery. The order is not significant.
   */
  selectedIds: $ReadOnlyArray<string>
): FetchingData.Fetched<{|
  /**
   * The URL of the configured iRODS server. This is set by the sysadmin and is
   * the same for all users on a particular instance.
   */
  serverUrl: string,

  /**
   * A list of folders on the iRODS server that the current user has configured
   * in the filestores section of the Gallery.
   */
  configuredLocations: $ReadOnlyArray<IrodsLocation>,
|}> {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  /**
   * Makes a new location on an iRODs server, which is to say a reference to a
   * folder. These objects are paramaterised (although it is not encoded in the
   * type) by the set of selected files as the copyLink and moveLinks encode
   * the information that instructs the server which files to move. As such,
   * two distinct IrodsLocation objects may exist in memory and whilst they may
   * refer to the same directory they encode information on how to copy and
   * move different files and are as such different IrodsLocations.
   */
  function mkIrodsLocation(
    id: IrodsLocation["id"],
    name: IrodsLocation["name"],
    path: IrodsLocation["path"],
    copyLink: Optional<string>,
    moveLink: Optional<string>
  ): IrodsLocation {
    return {
      id,
      name,
      path,
      copy: copyLink.map((cl) => async ({ username, password }) => {
        try {
          const api = axios.create({
            baseURL: "/api/v1/gallery/irods",
            headers: {
              Authorization: "Bearer " + (await getToken()),
            },
          });
          const response = await api.post<
            {|
              username: string,
              password: string,
            |},
            ""
          >(cl, {
            username,
            password,
          });
          addAlert(handleErrors(response));
        } catch (e) {
          console.error(e);
          const errorMsg = parseOperationError(e).orElseGet(([error]) => {
            throw new Error("Could not parse error object", { cause: error });
          });
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not copy file.",
              message: errorMsg,
            })
          );
          throw e;
        }
      }),
      move: moveLink.map((ml) => async ({ username, password }) => {
        try {
          const api = axios.create({
            baseURL: "/api/v1/gallery/irods",
            headers: {
              Authorization: "Bearer " + (await getToken()),
            },
          });
          const response = await api.post<
            {|
              username: string,
              password: string,
            |},
            ""
          >(ml, {
            username,
            password,
          });
          addAlert(handleErrors(response));
        } catch (e) {
          console.error(e);
          const errorMsg = parseOperationError(e).orElseGet(([error]) => {
            throw new Error("Could not parse error object", { cause: error });
          });
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not move file.",
              message: errorMsg,
            })
          );
          throw e;
        }
      }),
    };
  }

  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [configuredLocations, setConfiguredLocations] = React.useState<
    Result<$ReadOnlyArray<IrodsLocation>>
  >(Result.Ok([]));
  const [serverUrl, setServerUrl] = React.useState<Result<string>>(
    Result.Ok("")
  );

  async function fetchConfiguredLocations() {
    setLoading(true);
    setErrorMessage("");
    try {
      const api = axios.create({
        baseURL: "/api/v1/gallery/irods",
        headers: {
          Authorization: "Bearer " + (await getToken()),
        },
      });
      const { data } = await api.get<mixed>("/", {
        params: new URLSearchParams({
          recordIds: selectedIds.join(","),
        }),
      });
      const dataObj: Result<{ ... }> = Parsers.isObject(data).flatMap(
        Parsers.isNotNull
      );
      setServerUrl(
        dataObj
          .flatMap(Parsers.getValueWithKey("serverUrl"))
          .flatMap(Parsers.isString)
          .mapError(([error]) => {
            console.error("Cannot parse 'serverUrl' from API response", error);
            return new Error(
              "Could not determine which iRODS server is configured."
            );
          })
          .flatMap((url) =>
            url === ""
              ? Result.Error([new Error("No iRODS filestore configured")])
              : Result.Ok(url)
          )
      );
      setConfiguredLocations(
        dataObj
          .flatMap(Parsers.getValueWithKey("configuredLocations"))
          .flatMap(Parsers.isArray)
          .flatMap((array: $ReadOnlyArray<mixed>) => {
            return Result.all(
              ...array.map((x) =>
                Parsers.isObject(x)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) => {
                    const links = parseIrodsLocationLinks(obj);
                    return Result.lift5(mkIrodsLocation)(
                      Parsers.getValueWithKey("id")(obj).flatMap(
                        Parsers.isNumber
                      ),
                      Parsers.getValueWithKey("name")(obj).flatMap(
                        Parsers.isString
                      ),
                      Parsers.getValueWithKey("path")(obj).flatMap(
                        Parsers.isString
                      ),
                      links.map(parseSpecificHref("copy")),
                      links.map(parseSpecificHref("move"))
                    );
                  })
              )
            );
          })
      );
    } catch (e) {
      setErrorMessage(
        Parsers.objectPath(["response", "data"], e)
          .flatMap(Parsers.isObject)
          .flatMap(Parsers.isNotNull)
          .flatMap((data) => {
            Parsers.getValueWithKey("message")(data)
              .flatMap(Parsers.isString)
              .do((message) => console.error(message));
            return Parsers.getValueWithKey("errors")(data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head)
              .flatMap(Parsers.isString);
          })
          .orElse("Error parsing error")
      );
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  /*
   * This is a performance optimisation: we only need to re-fetch the
   * configured locations when the selection of files has materially changed. If
   * there is a new array in memory that contains the same Ids then there is no
   * need to re-fetch, nor if the same array has been re-ordered.
   */
  const parsedSelectedIds = Result.all(
    ...selectedIds.map(Parsers.parseInteger)
  ).orElseGet(() => {
    throw new Error("Invalid selected Ids");
  });
  const sortedStringOfSelectedIds = JSON.stringify(
    stableSort(parsedSelectedIds, (a, b) => {
      if (a > b) return 1;
      if (a < b) return -1;
      return 0;
    })
  );
  React.useEffect(() => {
    void fetchConfiguredLocations();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - fetchConfiguredLocations wont meaningfully change
     */
  }, [sortedStringOfSelectedIds]);

  if (loading) return { tag: "loading" };
  if (errorMessage) return { tag: "error", error: errorMessage };
  return Result.lift2(
    (url: string, locations: $ReadOnlyArray<IrodsLocation>) => ({
      tag: "success",
      value: {
        serverUrl: url,
        configuredLocations: locations,
      },
    })
  )(serverUrl, configuredLocations).orElseGet(([error]) => ({
    tag: "error",
    error: error.message,
  }));
}
