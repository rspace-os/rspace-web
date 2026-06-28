import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { useTranslation } from "react-i18next";
import { ErrorReason, type ErrorReasonType } from "./Enums";

type ErrorViewProps = {
  errorReason: ErrorReasonType;
  errorMessage: string;
};

export default function ErrorView({ errorReason, errorMessage }: ErrorViewProps) {
  const { t } = useTranslation("common");

  return (
    <Alert severity="error">
      <AlertTitle>{t("integrationErrors.title")}</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>
          The Omero server at{" "}
          <a
            // @ts-expect-error -- tinymce is defined in the parent window
            href={parent.tinymce?.activeEditor?.settings?.omero_url}
            target="_blank"
            rel="noreferrer"
          >
            {" "}
            {/* @ts-expect-error -- tinymce is defined in the parent window */}
            {parent.tinymce?.activeEditor?.settings?.omero_url}
          </a>{" "}
          is down, or CORS for this server has not been configured properly. If you are responsible for setting up the
          Omero integration, open developer tools and have a look at the console and/or the network tab to find out what
          the issue is.
        </>
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
