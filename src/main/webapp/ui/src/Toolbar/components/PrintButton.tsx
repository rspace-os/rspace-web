import { faPrint } from "@fortawesome/free-solid-svg-icons/faPrint";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import type React from "react";

type PrintButtonArgs = {
    dataTestId: string;
    onClick?: () => void;
};

export default function PrintButton({ dataTestId, onClick }: PrintButtonArgs): React.ReactNode {
    return (
        <Tooltip title="Print" enterDelay={300}>
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
