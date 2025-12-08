import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import Tooltip from "@mui/material/Tooltip";
import type React from "react";
import { useRef, useState } from "react";
import { makeStyles } from "tss-react/mui";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";

const useStyles = makeStyles()(() => ({
    splitButtonBox: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        borderRadius: "4px",
        height: "32px",
    },
    splitButtonGrid: {
        zIndex: 3,
    },
    iconBox: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        color: "default",
    },
    buttonLabel: {
        textTransform: "none",
        maxWidth: "85px",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
    },
}));

export type SplitButtonOption = {
    text: string;
    selection?: () => void;
};

type ContextMenuSplitButtonArgs = {
    options: Array<SplitButtonOption>;
    icon: React.ReactNode;
    loading?: boolean;
    disabledHelp?: string;
};

export default function ContextMenuSplitButton({
    options,
    icon,
    loading = false,
    disabledHelp = "",
}: ContextMenuSplitButtonArgs): React.ReactNode {
    const { classes } = useStyles();
    const [open, setOpen] = useState(false);
    const anchorRef = useRef<HTMLDivElement | null>(null);
    const [selectedIndex, setSelectedIndex] = useState(0);
    const [helpOpen, setHelpOpen] = useState(false);

    const handleTooltipClose = () => {
        setHelpOpen(false);
    };

    const handleMenuItemClick = (index: number) => {
        setSelectedIndex(index);
        setOpen(false);
        if (options[index].selection) options[index].selection();
    };

    const handleClose = (event: Event) => {
        if (anchorRef.current?.contains(event.target as Node)) {
            return;
        }

        setOpen(false);
    };

    return (
        <ClickAwayListener onClickAway={handleTooltipClose}>
            <div onClick={() => disabledHelp !== "" && setHelpOpen(true)}>
                <Tooltip
                    PopperProps={{
                        disablePortal: true,
                    }}
                    onClose={handleTooltipClose}
                    onOpen={() => disabledHelp !== "" && setHelpOpen(true)}
                    open={helpOpen}
                    title={disabledHelp}
                >
                    <Box className={classes.splitButtonBox}>
                        {false && (
                            <Box className={classes.iconBox} ml={0.5}>
                                {loading ? <FontAwesomeIcon icon="spinner" spin size="sm" /> : icon}
                            </Box>
                        )}
                        <Grid container direction="column" alignItems="center">
                            {/* high z-index to display it on top of Grid container */}
                            <Grid item xs={12} className={classes.splitButtonGrid}>
                                <ButtonGroup
                                    /* using "text" variant to integrate our button style, and add icon */
                                    variant="text"
                                    size="small"
                                    ref={anchorRef}
                                    aria-label="split button"
                                >
                                    <Button
                                        onClick={options[selectedIndex].selection}
                                        disabled={disabledHelp !== ""}
                                        variant="text"
                                        color="standardIcon"
                                        startIcon={loading ? <FontAwesomeIcon icon="spinner" spin size="sm" /> : icon}
                                    >
                                        <span className={classes.buttonLabel}>{options[selectedIndex].text}</span>
                                    </Button>
                                    <Button
                                        size="small"
                                        {...(open
                                            ? {
                                                  "aria-controls": "split-button-menu",
                                                  "aria-expanded": "true",
                                              }
                                            : {})}
                                        aria-haspopup="menu"
                                        aria-label="More selection options"
                                        onClick={() => setOpen(!open)}
                                        disabled={disabledHelp !== ""}
                                        variant="text"
                                        color="standardIcon"
                                    >
                                        <ArrowDropDownIcon fontSize="small" />
                                    </Button>
                                </ButtonGroup>
                                <StyledMenu anchorEl={anchorRef.current} open={open} onClose={handleClose}>
                                    {options.map((option, index) => (
                                        <StyledMenuItem
                                            key={index}
                                            selected={index === selectedIndex}
                                            aria-current={index === selectedIndex}
                                            onClick={() => handleMenuItemClick(index)}
                                        >
                                            <ListItemText primary={option.text} />
                                        </StyledMenuItem>
                                    ))}
                                </StyledMenu>
                            </Grid>
                        </Grid>
                    </Box>
                </Tooltip>
            </div>
        </ClickAwayListener>
    );
}
