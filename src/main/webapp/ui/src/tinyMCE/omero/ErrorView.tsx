import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { ErrorReason, type ErrorReasonType } from "./Enums";

type ErrorViewProps = {
  errorReason: ErrorReasonType;
  errorMessage: string;
};

export default function ErrorView({ errorReason, errorMessage }: ErrorViewProps) {
  const { t } = useTranslation("common");
  // @ts-expect-error -- tinymce is defined in the parent window
  const omeroUrl = parent.tinymce?.activeEditor?.settings?.omero_url;

  return (
    <Alert severity="error">
      <AlertTitle>{t("integrationErrors.title")}</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <TransRichText
          i18nKey="common:integrationErrors.serverUnavailable"
          values={{ appName: "Omero", url: omeroUrl }}
          components={{
            serverLink: (
              <a href={omeroUrl} target="_blank" rel="noreferrer">
                {omeroUrl}
              </a>
            ),
          }}
        />
      )}
      {errorReason === ErrorReason.NotFound && t("integrationErrors.omero.notFound")}
      {/* when user credentials expire on user session end, server responds with 401 */}
      {(errorReason === ErrorReason.Unauthorized || errorMessage.includes("invalid_grant")) &&
        t("integrationErrors.omero.sessionExpired")}
      {errorReason === ErrorReason.Timeout && t("integrationErrors.timeout")}
      {/* when a refresh token expires the omero API responds with 400 response and 'invalid_grant' in the response message */}
      {errorReason === ErrorReason.BadRequest &&
        !errorMessage.includes("invalid_grant") &&
        t("integrationErrors.tryAgainLater")}
      {errorReason === ErrorReason.UNKNOWN && t("integrationErrors.unknownRelogin")}
    </Alert>
  );
}
