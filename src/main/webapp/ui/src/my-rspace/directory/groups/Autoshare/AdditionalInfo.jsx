import React from "react";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";

function AdditionalInfo() {
  const [showInfo, setShowInfo] = React.useState(false);

  return (
    <Alert variant="outlined" severity="info">
      <AlertTitle>
        Additional Information
        <Tooltip
          title="Toggle additional information"
          aria-label="Toggle additional information"
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
          <p>
            Enabling autosharing for a member will ensure that all their current
            and future documents and notebooks will be shared automatically with
            the "read" permission with this group.
          </p>
          <p>
            Edit permission can be granted or items can be unshared through the
            "Manage Shared Documents" section as usual.
          </p>
          <p>
            This setting is revertible and you can always manually change the
            autoshare status of any non-PI member in your lab group.
          </p>
        </>
      )}
    </Alert>
  );
}

export default AdditionalInfo;
