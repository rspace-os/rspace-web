import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AlertContext, { type Alert, mkAlert } from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";
import type * as FetchingData from "../../../util/fetchingData";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";

const parseOperationError = (error: unknown): Result<string> =>
  Parsers.objectPath(["response", "data", "errors"], error)
    .flatMap(Parsers.isArray)
    .flatMap(ArrayUtils.head)
    .flatMap(Parsers.isString)
    .map((errorMsg) => {
      if (/attempt to overwrite file/.test(errorMsg)) return i18n.t("gallery:irods.errors.filesAlreadyExist");
      if (/AuthenticationException/.test(errorMsg)) return i18n.t("gallery:irods.errors.authenticationFailed");
      if (/InvalidUserException/.test(errorMsg)) return i18n.t("gallery:irods.errors.authenticationFailed");
      if (/java.net.UnknownHostException/.test(errorMsg)) return i18n.t("gallery:irods.errors.couldNotReachServer");
      return errorMsg;
    });

function handleErrors(
  response: unknown,
  successMessage: string = i18n.t("gallery:irods.success.moved"),
  partialFailureMessage: string = i18n.t("gallery:irods.errors.partialMoveFailed"),
): Alert {
  const data = Parsers.objectPath(["data"], response).flatMap(Parsers.isObject).flatMap(Parsers.isNotNull);

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
                message: successMessage,
              }),
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
                      const succeeded = Parsers.getValueWithKey("succeeded")(obj)
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
                              variant: "success" as const,
                              title: filename,
                            })),
                        )
                        .orElseTry(() =>
                          /*
                           * Otherwise parse filename and reason, and show a red
                           * alert
                           */
                          Result.lift2((filename: string, reason: string) => ({
                            variant: "error" as const,
                            title: filename,
                            help: reason,
                          }))(
                            Parsers.getValueWithKey("fileName")(obj).flatMap(Parsers.isString),
                            Parsers.getValueWithKey("reason")(obj).flatMap(Parsers.isString),
                          ),
                        );
                    }),
                ),
              ).map((details) =>
                mkAlert({
                  variant: "warning",
                  message: partialFailureMessage,
                  details,
                  isInfinite: true,
                }),
              ),
            );
        }),
    )
    .orElse(
      mkAlert({
        variant: "error",
        message: i18n.t("gallery:errors.parseResponse"),
      }),
    );
}

/**
 * Credentials for an iRODS filesystem. iRODS auth is per-filesystem, so these
 * are collected (and cached server-side) per destination filesystem rather
 * than globally.
 */
export type IrodsCredentials = { username: string; password: string };

/**
 * A folder (filestore) on an iRODS system.
 *
 * Unlike the previous implementation, which only ever surfaced filestores on
 * the first connected iRODS filesystem, locations are now built from the
 * generic GET /api/v1/gallery/filestores listing and span ALL connected iRODS
 * filesystems. Each location therefore carries the identity of the filesystem
 * it lives on (filesystemId / filesystemName / filesystemUrl) plus its
 * authType, so the dialog can group destinations by server and collect the
 * right per-filesystem credentials.
 */
export type IrodsLocation = {
  id: number;
  name: string;
  path: string;
  filesystemId: number;
  filesystemName: string;
  filesystemUrl: string;
  authType: string;
  copy: (recordIds: ReadonlyArray<number>, credentials: IrodsCredentials) => Promise<void>;
  move: (recordIds: ReadonlyArray<number>, credentials: IrodsCredentials) => Promise<void>;
};

/**
 * A custom hook that fetches the list of iRODS filestores from
 * GET /api/v1/gallery/filestores (filtering for clientType === "IRODS", across
 * every connected iRODS filesystem) and provides move/copy operations for each.
 *
 * This mirrors the S3 flow (see useS3Filestores): the listing is not
 * parameterised by the selected files, and move/copy are keyed by filestore id
 * with the record ids supplied in the request body. iRODS additionally requires
 * per-filesystem credentials, which are passed through to the move/copy body.
 */
export default function useIrods(): FetchingData.Fetched<ReadonlyArray<IrodsLocation>> {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("gallery");
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [configuredLocations, setConfiguredLocations] = React.useState<Result<ReadonlyArray<IrodsLocation>>>(
    Result.Ok([]),
  );

  function mkIrodsLocation(location: Omit<IrodsLocation, "copy" | "move">): IrodsLocation {
    const { id } = location;
    async function callEndpoint(
      operation: "move" | "copy",
      recordIds: ReadonlyArray<number>,
      { username, password }: IrodsCredentials,
    ): Promise<void> {
      try {
        const api = axios.create({
          baseURL: "/api/v1/gallery",
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        });
        // Gallery -> filestore ingest is the unified /uploadFromGallery endpoint
        // (see useS3Filestores): removeOriginalFromRspace=true is a "move", false
        // is a "copy". iRODS requires per-filesystem credentials in the body; S3
        // ignores them.
        const response = await api.post<"">(`/filestores/${id}/uploadFromGallery`, {
          recordIds,
          credentials: { username, password },
          removeOriginalFromRspace: operation === "move",
        });
        addAlert(
          handleErrors(
            response,
            operation === "copy" ? t("irods.success.copied") : t("irods.success.moved"),
            operation === "copy" ? t("irods.errors.partialCopyFailed") : t("irods.errors.partialMoveFailed"),
          ),
        );
      } catch (e) {
        console.error(e);
        // Fall back to a safe message rather than throwing while handling the
        // error, which would suppress the user-facing alert (mirrors the S3 flow).
        const errorMsg = parseOperationError(e).orElse(t("errors.unknownError"));
        addAlert(
          mkAlert({
            variant: "error",
            title: operation === "copy" ? t("irods.errors.copyFailed") : t("irods.errors.moveFailed"),
            message: errorMsg,
          }),
        );
        throw e;
      }
    }

    return {
      ...location,
      copy: (recordIds, credentials) => callEndpoint("copy", recordIds, credentials),
      move: (recordIds, credentials) => callEndpoint("move", recordIds, credentials),
    };
  }

  async function fetchConfiguredLocations() {
    setLoading(true);
    setErrorMessage("");
    try {
      const api = axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      const { data } = await api.get<unknown>("/filestores");

      setConfiguredLocations(
        Parsers.isArray(data).flatMap((array: ReadonlyArray<unknown>) => {
          const irodsOnly = array.filter((item) =>
            Parsers.isObject(item)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("fileSystem"))
              .flatMap(Parsers.isObject)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("clientType"))
              .flatMap(Parsers.isString)
              .map((type) => type === "IRODS")
              .orElse(false),
          );

          return Result.all(
            ...irodsOnly.map((item) =>
              Parsers.isObject(item)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  // The seven fields are independent, so parse each one flatly
                  // and let the first failure short-circuit via elseThrow; the
                  // catch turns it back into the Error that Result.all
                  // propagates. (See result.ts: elseThrow is the sanctioned
                  // alternative to deeply nested flatMaps / lifts.)
                  const fileSystem = Parsers.getValueWithKey("fileSystem")(obj)
                    .flatMap(Parsers.isObject)
                    .flatMap(Parsers.isNotNull);
                  try {
                    return Result.Ok(
                      mkIrodsLocation({
                        id: Parsers.getValueWithKey("id")(obj).flatMap(Parsers.isNumber).elseThrow(),
                        name: Parsers.getValueWithKey("name")(obj).flatMap(Parsers.isString).elseThrow(),
                        path: Parsers.getValueWithKey("path")(obj).flatMap(Parsers.isString).elseThrow(),
                        filesystemId: fileSystem
                          .flatMap(Parsers.getValueWithKey("id"))
                          .flatMap(Parsers.isNumber)
                          .elseThrow(),
                        filesystemName: fileSystem
                          .flatMap(Parsers.getValueWithKey("name"))
                          .flatMap(Parsers.isString)
                          .elseThrow(),
                        filesystemUrl: fileSystem
                          .flatMap(Parsers.getValueWithKey("url"))
                          .flatMap(Parsers.isString)
                          .elseThrow(),
                        authType: fileSystem
                          .flatMap(Parsers.getValueWithKey("authType"))
                          .flatMap(Parsers.isString)
                          .elseThrow(),
                      }),
                    );
                  } catch (e) {
                    return Result.Error<IrodsLocation>(
                      e instanceof Error ? [e] : [new Error("Could not parse iRODS filestore")],
                    );
                  }
                }),
            ),
          );
        }),
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
          .orElse(t("errors.parseResponse")),
      );
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    void fetchConfiguredLocations();
  }, []);

  if (loading) return { tag: "loading" };
  if (errorMessage) return { tag: "error", error: errorMessage };
  return configuredLocations
    .map((locations) => ({ tag: "success" as const, value: locations }))
    .orElseGet(([error]) => ({ tag: "error", error: error.message }));
}
