import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import React from "react";
import { useTranslation } from "react-i18next";

function AdditionalInfo() {
  const { t } = useTranslation("common");
  const [showInfo, setShowInfo] = React.useState(false);

  return (
    <Alert variant="outlined" severity="info">
      <AlertTitle>
        {t("profile.groups.autosharing.additionalInfo.title")}
        <Tooltip
          title={t("profile.groups.autosharing.additionalInfo.toggle")}
          aria-label={t("profile.groups.autosharing.additionalInfo.toggle")}
        >
          <IconButton
            size="small"
            onClick={(e) => {
              setShowInfo(!showInfo);
              e.currentTarget.blur();
            }}
          >
            {showInfo ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </Tooltip>
      </AlertTitle>
      {showInfo && (
        <>
          <p>{t("profile.groups.autosharing.additionalInfo.p1")}</p>
          <p>{t("profile.groups.autosharing.additionalInfo.p2")}</p>
          <p>{t("profile.groups.autosharing.additionalInfo.p3")}</p>
        </>
      )}
    </Alert>
  );
}

export default AdditionalInfo;
