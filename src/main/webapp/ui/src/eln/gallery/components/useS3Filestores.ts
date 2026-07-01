import React from "react";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AlertContext, { type Alert, mkAlert } from "../../../stores/contexts/Alert";
import type * as FetchingData from "../../../util/fetchingData";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";

const firstResult = <T>(items: ReadonlyArray<T>): Result<T> =>
  Result.fromNullable(items.at(0), new Error("Array is empty"));

function handleErrors(
  response: unknown,
  successMessage: string = i18n.t("gallery:s3.success.moved"),
  partialFailureMessage: string = i18n.t("gallery:s3.errors.partialMoveFailed"),
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

export type S3TransferSource = {
  sourceFilestoreId: number;
  sourcePath: string;
};

/**
 * An S3 filestore configured by the user in the Gallery's filestore section.
 *
 * canRead/canWrite reflect the per-user ACL for the underlying filesystem; a stale
 * filestore (one the user has lost access to) is still returned by the listing
 * endpoint with canRead=false, so the UI can render it as inaccessible rather than
 * silently dropping it.
 */
export type S3Filestore = {
  id: number;
  name: string;
  canRead: boolean;
  canWrite: boolean;
  copy: (recordIds: ReadonlyArray<number>) => Promise<void>;
  move: (recordIds: ReadonlyArray<number>) => Promise<void>;
  transfer: (sources: ReadonlyArray<S3TransferSource>, deleteSource: boolean) => Promise<void>;
};

/**
 * A custom hook that fetches the list of S3 filestores from
 * GET /api/v1/gallery/filestores (filtering for clientType === "S3") and
 * provides move/copy operations for each.
 */
export default function useS3Filestores(): FetchingData.Fetched<ReadonlyArray<S3Filestore>> {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [filestores, setFilestores] = React.useState<Result<ReadonlyArray<S3Filestore>>>(Result.Ok([]));

  function mkS3Filestore(id: number, name: string, canRead: boolean, canWrite: boolean): S3Filestore {
    async function callEndpoint(operation: "move" | "copy", recordIds: ReadonlyArray<number>): Promise<void> {
      try {
        const api = axios.create({
          baseURL: "/api/v1/gallery",
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        });
        const response = await api.post<unknown>(`/filestores/${id}/uploadFromGallery`, {
          recordIds,
          removeOriginalFromRspace: operation === "move",
        });
        addAlert(
          handleErrors(
            response,
            operation === "copy" ? i18n.t("gallery:s3.success.copied") : i18n.t("gallery:s3.success.moved"),
            operation === "copy"
              ? i18n.t("gallery:s3.errors.partialCopyFailed")
              : i18n.t("gallery:s3.errors.partialMoveFailed"),
          ),
        );
      } catch (e) {
        console.error(e);
        const errorMsg = Parsers.objectPath(["response", "data", "errors"], e)
          .flatMap(Parsers.isArray)
          .flatMap(firstResult)
          .flatMap(Parsers.isString)
          .orElse(i18n.t("gallery:errors.unknownError"));
        addAlert(
          mkAlert({
            variant: "error",
            title:
              operation === "copy" ? i18n.t("gallery:s3.errors.copyFailed") : i18n.t("gallery:s3.errors.moveFailed"),
            message: errorMsg,
          }),
        );
        throw e;
      }
    }

    async function callTransferEndpoint(
      sources: ReadonlyArray<S3TransferSource>,
      deleteSource: boolean,
    ): Promise<void> {
      try {
        const api = axios.create({
          baseURL: "/api/v1/gallery",
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        });
        for (const { sourceFilestoreId, sourcePath } of sources) {
          const destPath = sourcePath.split("/").filter(Boolean).pop() ?? sourcePath;
          const response = await api.post<unknown>(`/filestores/${sourceFilestoreId}/transfer`, {
            sourcePath,
            destFilestoreId: id,
            destPath,
            deleteSource,
          });
          addAlert(
            handleErrors(
              response,
              i18n.t("gallery:s3.success.transferred"),
              i18n.t("gallery:s3.errors.partialTransferFailed"),
            ),
          );
        }
      } catch (e) {
        console.error(e);
        const errorMsg = Parsers.objectPath(["response", "data", "errors"], e)
          .flatMap(Parsers.isArray)
          .flatMap(firstResult)
          .flatMap(Parsers.isString)
          .orElse(i18n.t("gallery:errors.unknownError"));
        addAlert(
          mkAlert({
            variant: "error",
            title: i18n.t("gallery:s3.errors.transferFailed"),
            message: errorMsg,
          }),
        );
        throw e;
      }
    }

    return {
      id,
      name,
      canRead,
      canWrite,
      copy: (recordIds) => callEndpoint("copy", recordIds),
      move: (recordIds) => callEndpoint("move", recordIds),
      transfer: (sources, deleteSource) => callTransferEndpoint(sources, deleteSource),
    };
  }

  async function fetchFilestores() {
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

      setFilestores(
        Parsers.isArray(data).flatMap((array: ReadonlyArray<unknown>) => {
          const s3Only = array.filter((item) =>
            Parsers.isObject(item)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("fileSystem"))
              .flatMap(Parsers.isObject)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("clientType"))
              .flatMap(Parsers.isString)
              .map((type) => type === "S3")
              .orElse(false),
          );

          return Result.all(
            ...s3Only.map((item) =>
              Parsers.isObject(item)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  // userPermissions may be absent (older backend, or non-NONE auth)
                  // — default canRead/canWrite to true so the UI is permissive
                  // when the backend didn't supply a permissions snapshot.
                  const perms = Parsers.getValueWithKey("userPermissions")(obj)
                    .flatMap(Parsers.isObject)
                    .flatMap(Parsers.isNotNull);
                  const canRead = perms
                    .flatMap(Parsers.getValueWithKey("canRead"))
                    .flatMap(Parsers.isBoolean)
                    .orElse(true);
                  const canWrite = perms
                    .flatMap(Parsers.getValueWithKey("canWrite"))
                    .flatMap(Parsers.isBoolean)
                    .orElse(true);
                  return Result.lift2(
                    (id: number, name: string): S3Filestore => mkS3Filestore(id, name, canRead, canWrite),
                  )(
                    Parsers.getValueWithKey("id")(obj).flatMap(Parsers.isNumber),
                    Parsers.getValueWithKey("name")(obj).flatMap(Parsers.isString),
                  );
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
          .flatMap(Parsers.getValueWithKey("errors"))
          .flatMap(Parsers.isArray)
          .flatMap(firstResult)
          .flatMap(Parsers.isString)
          .orElse(i18n.t("gallery:listing.alerts.retrieveFilestoresFailed")),
      );
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  React.useEffect(() => {
    void fetchFilestores();
  }, []);

  if (loading) return { tag: "loading" };
  if (errorMessage) return { tag: "error", error: errorMessage };
  return filestores
    .map((fs) => ({ tag: "success" as const, value: fs }))
    .orElseGet(([error]) => ({ tag: "error", error: error.message }));
}
