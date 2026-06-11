import React, { useState } from "react";
import { observer } from "mobx-react-lite";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import RefreshIcon from "@mui/icons-material/Refresh";

type RetryArgs = {
  retryFunction: () => Promise<void>;
  onClose: () => void;
};

function Retry({ retryFunction, onClose }: RetryArgs): React.ReactNode {
  const [retrying, setRetrying] = useState(false);
  return (
    <IconButtonWithTooltip
      title="Retry"
      icon={
        retrying ? (
          <FontAwesomeIcon icon={faSpinner} spin size="sm" />
        ) : (
          <RefreshIcon />
        )
      }
      onClick={(_event: React.MouseEvent<HTMLButtonElement>) => {
        void (async () => {
          setRetrying(true);
          try {
            await retryFunction();
          } finally {
            onClose();
          }
        })();
      }}
      sx={{ m: 0.5 }}
    />
  );
}

/**
 * The retry button for the alert toasts.
 */
export default observer(Retry);
