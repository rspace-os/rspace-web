import CardMedia from "@mui/material/CardMedia";
import React from "react";
import { LOGO_COLOR } from "../../assets/branding/argos";
import ArgosIcon from "../../assets/branding/argos/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";
import EventBoundary from "../../components/EventBoundary";
import DMPDialog from "./DMPDialog";

type ArgosAccentMenuItemArgs = {
    onDialogClose: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from Argos.
 */
export default function ArgosMenuItem({ onDialogClose }: ArgosAccentMenuItemArgs): React.ReactNode {
    const [showDMPDialog, setShowDMPDialog] = React.useState(false);

    return (
        <>
            <AccentMenuItem
                title="Argos"
                avatar={<CardMedia image={ArgosIcon} />}
                backgroundColor={LOGO_COLOR}
                foregroundColor={{ ...LOGO_COLOR, lightness: 28 }}
                onClick={() => {
                    setShowDMPDialog(true);
                }}
                aria-haspopup="dialog"
                compact
            />
            <EventBoundary>
                <DMPDialog
                    open={showDMPDialog}
                    setOpen={(b) => {
                        setShowDMPDialog(b);
                        if (!b) onDialogClose();
                    }}
                />
            </EventBoundary>
        </>
    );
}
