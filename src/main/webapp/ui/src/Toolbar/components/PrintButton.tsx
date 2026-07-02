import { faPrint } from "@fortawesome/free-solid-svg-icons/faPrint";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import type React from "react";
import { useTranslation } from "react-i18next";

type PrintButtonArgs = {
  "data-test-id": string;
  onClick?: () => void;
};

export default function PrintButton({ "data-test-id": dataTestId, onClick }: PrintButtonArgs): React.ReactNode {
  const { t } = useTranslation("common");

  return (
    <Tooltip title={t("toolbar.print")} enterDelay={300}>
      <IconButton
        data-test-id={dataTestId}
        color="inherit"
        onClick={(e) => {
          e.preventDefault();
          onClick?.();
          window.print();
        }}
      >
        <FontAwesomeIcon icon={faPrint} />
      </IconButton>
    </Tooltip>
  );
}
