import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { useTranslation } from "react-i18next";
import { ErrorReason } from "./Enums";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function ErrorView({ errorReason }: { errorReason: any }) {
  const { t } = useTranslation("apps");

  return (
    <Alert severity="error">
      <AlertTitle>{t("pyrat.error.title")}</AlertTitle>
      {errorReason === ErrorReason.NetworkError && <>{t("pyrat.error.network")}</>}
      {errorReason === ErrorReason.APIVersion && <>{t("pyrat.error.apiVersion")}</>}
      {errorReason === ErrorReason.Unauthorized && <>{t("pyrat.error.unauthorized")}</>}
      {errorReason === ErrorReason.Timeout && <>{t("pyrat.error.timeout")}</>}
      {errorReason === ErrorReason.BadRequest && <>{t("pyrat.error.badRequest")}</>}
      {errorReason === ErrorReason.Unknown && <>{t("pyrat.error.unknown")}</>}
    </Alert>
  );
}
