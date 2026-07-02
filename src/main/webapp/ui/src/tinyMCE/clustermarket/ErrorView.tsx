import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { ErrorReason } from "./Enums";

export default function ErrorView({
  errorReason,
  errorMessage,
}: {
  errorReason: (typeof ErrorReason)[keyof typeof ErrorReason];
  errorMessage: string;
}): React.ReactNode {
  const { t } = useTranslation("common");
  // @ts-expect-error -- tinymce is defined in the parent window
  const clustermarketUrl = parent.tinymce.activeEditor.settings.clustermarket_url;

  return (
    <Alert severity="error">
      <AlertTitle>{t("integrationErrors.title")}</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <TransRichText
          ns="common"
          i18nKey="integrationErrors.serverUnavailable"
          values={{ appName: "Calira", url: clustermarketUrl }}
          components={{
            serverLink: (
              <a href={clustermarketUrl} target="_blank" rel="noreferrer">
                {clustermarketUrl}
              </a>
            ),
          }}
        />
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
