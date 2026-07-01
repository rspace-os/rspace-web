import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type React from "react";
import { useTranslation } from "react-i18next";
import { ErrorReason } from "./Enums";

export default function ErrorView({
  errorReason,
  errorMessage,
}: {
  errorReason: (typeof ErrorReason)[keyof typeof ErrorReason];
  errorMessage: string;
}): React.ReactNode {
  const { t } = useTranslation("common");

  return (
    <Alert severity="error">
      <AlertTitle>{t("integrationErrors.title")}</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>
          {"The Calira server at "}
          <a
            // @ts-expect-error -- tinymce is defined in the parent window
            href={parent.tinymce.activeEditor.settings.clustermarket_url}
            target="_blank"
            rel="noreferrer"
          >
            {" "}
            {/* @ts-expect-error -- tinymce is defined in the parent window */}
            {parent.tinymce.activeEditor.settings.clustermarket_url}
          </a>
          {
            " is down, or CORS for this server has not been configured properly. If you are responsible for setting up the Calira integration, open developer tools and have a look at the console and/or the network tab to find out what the issue is."
          }
        </>
      )}
      {errorReason === ErrorReason.NotFound && t("integrationErrors.calira.notFound")}
      {/* when an OAuth token expires the Clustermarket API responds with 401 response.
        When a refresh token expires the Clustermarket API responds with 400 response and 'invalid_grant' in the response message */}
      {(errorReason === ErrorReason.Unauthorized || errorMessage.includes("invalid_grant")) &&
        t("integrationErrors.calira.invalidToken")}
      {errorReason === ErrorReason.Timeout && t("integrationErrors.timeout")}
      {/* when a refresh token expires the Clustermarket API responds with 400 response and 'invalid_grant' in the response message */}
      {errorReason === ErrorReason.BadRequest &&
        !errorMessage.includes("invalid_grant") &&
        t("integrationErrors.tryAgainLater")}
      {errorReason === ErrorReason.UNKNOWN && t("integrationErrors.unknownRelogin")}
    </Alert>
  );
}
