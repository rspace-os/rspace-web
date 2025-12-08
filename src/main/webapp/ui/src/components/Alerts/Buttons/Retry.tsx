import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import RefreshIcon from "@mui/icons-material/Refresh";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { doNotAwait } from "../../../util/Util";
import IconButtonWithTooltip from "../../IconButtonWithTooltip";

type RetryArgs = {
    retryFunction: () => Promise<void>;
    onClose: () => void;
};

function Retry({ retryFunction, onClose }: RetryArgs): React.ReactNode {
    const [retrying, setRetrying] = useState(false);
    return (
        <IconButtonWithTooltip
            title="Retry"
            icon={retrying ? <FontAwesomeIcon icon={faSpinner} spin size="sm" /> : <RefreshIcon />}
            onClick={doNotAwait(async (_event: React.MouseEvent<HTMLButtonElement>) => {
                setRetrying(true);
                try {
                    await retryFunction();
                } finally {
                    onClose();
                }
            })}
            sx={{ m: 0.5 }}
        />
    );
}

/**
 * The retry button for the alert toasts.
 */
export default observer(Retry);
