import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../../components/GlobalId";
import type { Template } from "../../../stores/definitions/Template";

type VersionInfoArgs = {
  template: Template;
  onUpdate?: () => void;
  disabled?: boolean;
};

function VersionInfo({ template, onUpdate, disabled }: VersionInfoArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  React.useEffect(() => {
    template.getLatest();
  }, []);

  return template.historicalVersion ? (
    <Alert
      severity="info"
      action={
        onUpdate && (
          <Box sx={{ mr: 2 }}>
            <Button color="inherit" size="small" variant="outlined" onClick={onUpdate} disabled={disabled}>
              {t("template.fields.versionInfo.update")}
            </Button>
          </Box>
        )
      }
    >
      <AlertTitle>{t("template.fields.versionInfo.version", { version: template.version })}</AlertTitle>
      {template.latest && (
        <span>
          {t("template.fields.versionInfo.latestVersion")} <GlobalId record={template.latest} />
        </span>
      )}
    </Alert>
  ) : null;
}

export default observer(VersionInfo);
