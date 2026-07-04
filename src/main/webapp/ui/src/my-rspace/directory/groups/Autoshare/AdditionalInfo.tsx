import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "../../../../modules/common/i18n/TransRichText";

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
        <TransRichText i18nKey="common:profile.groups.autosharing.additionalInfo.body" components={{ p: <p /> }} />
      )}
    </Alert>
  );
}

export default AdditionalInfo;
