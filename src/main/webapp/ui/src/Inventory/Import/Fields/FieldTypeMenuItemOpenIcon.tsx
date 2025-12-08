import CloseIcon from "@mui/icons-material/Close";
import ListIcon from "@mui/icons-material/List";
import type React from "react";

export default function FieldTypeMenuItemOpenIcon({ open }: { open: boolean }): React.ReactNode {
    return open ? <CloseIcon /> : <ListIcon />;
}
