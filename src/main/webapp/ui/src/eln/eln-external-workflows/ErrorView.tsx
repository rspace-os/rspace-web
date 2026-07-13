import CloseIcon from "@mui/icons-material/Close";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import type SvgIcon from "@mui/material/SvgIcon";
import React from "react";
import { useTranslation } from "react-i18next";
import { ErrorReason } from "./Enums";

export default function ErrorView({
  errorReason,
  errorMessage,
  WorkFlowIcon,
}: {
  errorReason: (typeof ErrorReason)[keyof typeof ErrorReason];
  errorMessage: string;
  WorkFlowIcon: typeof SvgIcon;
}): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const [open, setOpen] = React.useState(true);
  return (
    <>
      <Collapse in={open}>
        <Alert severity="error" icon={<WorkFlowIcon fontSize="inherit" />}>
          <AlertTitle>{t("externalWorkflows.error.title")}</AlertTitle>
          <IconButton
            aria-label={t("common:actions.close")}
            color="inherit"
            size="large"
            onClick={() => {
              setOpen(false);
            }}
          >
            <CloseIcon fontSize="inherit" />
          </IconButton>
          {/* When Galaxy API KEY is invalid Galaxy API responds with 403 */}
          {errorReason === ErrorReason.Unauthorized && <>{t("externalWorkflows.error.unauthorized")}</>}
          {errorReason === ErrorReason.Timeout && <>{t("externalWorkflows.error.timeout")}</>}
          {errorReason === ErrorReason.NotFound && <>{t("externalWorkflows.error.notFound", { errorMessage })}</>}

          {errorReason === ErrorReason.UNKNOWN && <>{t("externalWorkflows.error.unknown", { errorMessage })}</>}
          {errorReason === ErrorReason.None && errorMessage && <> {errorMessage}</>}
        </Alert>
      </Collapse>
      <Button
        disabled={open}
        variant="outlined"
        onClick={() => {
          setOpen(true);
        }}
      >
        {t("externalWorkflows.error.seeError")}
      </Button>
    </>
  );
}
