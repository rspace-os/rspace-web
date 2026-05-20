import axios from "@/common/axios";
import React from "react";
import Result from "../../../util/result";
import * as Parsers from "../../../util/parsers";
import * as FetchingData from "../../../util/fetchingData";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AlertContext, {
  mkAlert,
  type Alert,
} from "../../../stores/contexts/Alert";
import * as ArrayUtils from "../../../util/ArrayUtils";

function handleErrors(
  response: unknown,
  successMessage = "Successfully moved the files to S3.",
  partialFailureMessage = "Moving some files to S3 failed.",
): Alert {
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
                      const succeeded = Parsers.getValueWithKey("succeeded")(
                        obj,
                      )
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
                            Parsers.getValueWithKey("fileName")(obj).flatMap(
                              Parsers.isString,
                            ),
                            Parsers.getValueWithKey("reason")(obj).flatMap(
                              Parsers.isString,
                            ),
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
        message: "Could not parse response.",
      }),
    );
}

export type S3TransferSource = {
  sourceFilestoreId: number;
  sourcePath: string;
};

/**
 * An S3 filestore configured by the user in the Gallery's filestore section.
 */
export type S3Filestore = {
  id: number;
  name: string;
  copy: (recordIds: ReadonlyArray<number>) => Promise<void>;
  move: (recordIds: ReadonlyArray<number>) => Promise<void>;
  transfer: (
    sources: ReadonlyArray<S3TransferSource>,
    deleteSource: boolean,
  ) => Promise<void>;
};

/**
 * A custom hook that fetches the list of S3 filestores from
 * GET /api/v1/gallery/filestores (filtering for clientType === "S3") and
 * provides move/copy operations for each.
 */
export default function useS3Filestores(): FetchingData.Fetched<
  ReadonlyArray<S3Filestore>
> {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [filestores, setFilestores] = React.useState<
    Result<ReadonlyArray<S3Filestore>>
  >(Result.Ok([]));

  function mkS3Filestore(id: number, name: string): S3Filestore {
    async function callEndpoint(
      operation: "move" | "copy",
      recordIds: ReadonlyArray<number>,
    ): Promise<void> {
      try {
        const api = axios.create({
          baseURL: "/api/v1/gallery",
          headers: {
            Authorization: "Bearer " + (await getToken()),
          },
        });
        const response = await api.post<unknown>(
          `/filestores/${id}/${operation}`,
          { recordIds },
        );
        addAlert(
          handleErrors(
            response,
            operation === "copy"
              ? "Successfully copied the files to S3."
              : "Successfully moved the files to S3.",
            operation === "copy"
              ? "Copying some files to S3 failed."
              : "Moving some files to S3 failed.",
          ),
        );
      } catch (e) {
        console.error(e);
        const errorMsg = Parsers.objectPath(["response", "data", "errors"], e)
          .flatMap(Parsers.isArray)
          .flatMap(ArrayUtils.head)
          .flatMap(Parsers.isString)
          .orElse("Unknown error");
        addAlert(
          mkAlert({
            variant: "error",
            title: `Could not ${operation} files to S3.`,
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
            Authorization: "Bearer " + (await getToken()),
          },
        });
        for (const { sourceFilestoreId, sourcePath } of sources) {
          const destPath =
            sourcePath.split("/").filter(Boolean).pop() ?? sourcePath;
          const response = await api.post<unknown>(
            `/filestores/${sourceFilestoreId}/transfer`,
            { sourcePath, destFilestoreId: id, destPath, deleteSource },
          );
          addAlert(
            handleErrors(
              response,
              "Successfully transferred the files to S3.",
              "Transferring some files to S3 failed.",
            ),
          );
        }
      } catch (e) {
        console.error(e);
        const errorMsg = Parsers.objectPath(["response", "data", "errors"], e)
          .flatMap(Parsers.isArray)
          .flatMap(ArrayUtils.head)
          .flatMap(Parsers.isString)
          .orElse("Unknown error");
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not transfer files to S3.",
            message: errorMsg,
          }),
        );
        throw e;
      }
    }

    return {
      id,
      name,
      copy: (recordIds) => callEndpoint("copy", recordIds),
      move: (recordIds) => callEndpoint("move", recordIds),
      transfer: (sources, deleteSource) =>
        callTransferEndpoint(sources, deleteSource),
    };
  }

  async function fetchFilestores() {
    setLoading(true);
    setErrorMessage("");
    try {
      const api = axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: "Bearer " + (await getToken()),
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
                .flatMap((obj) =>
                  Result.lift2(mkS3Filestore)(
                    Parsers.getValueWithKey("id")(obj).flatMap(
                      Parsers.isNumber,
                    ),
                    Parsers.getValueWithKey("name")(obj).flatMap(
                      Parsers.isString,
                    ),
                  ),
                ),
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
          .flatMap(ArrayUtils.head)
          .flatMap(Parsers.isString)
          .orElse("Error loading S3 filestores"),
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
