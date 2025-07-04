import React from "react";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { library } from "@fortawesome/fontawesome-svg-core";
import { faPrint } from "@fortawesome/free-solid-svg-icons";
library.add(faPrint);

type PrintButtonArgs = {
  dataTestId: string;
};

export default function PrintButton({
  dataTestId,
}: PrintButtonArgs): React.ReactNode {
  return (
    <Tooltip title="Print" enterDelay={300}>
      <IconButton
        data-test-id={dataTestId}
        color="inherit"
        onClick={(e) => {
          e.preventDefault();
          window.print();
        }}
      >
        <FontAwesomeIcon icon="print" />
      </IconButton>
    </Tooltip>
  );
}
