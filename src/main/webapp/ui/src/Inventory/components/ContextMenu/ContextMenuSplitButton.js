// @flow

import React, { useState, useRef, type Node } from "react";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import { makeStyles } from "tss-react/mui";
import Box from "@mui/material/Box";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Tooltip from "@mui/material/Tooltip";
import { StyledMenuItem, StyledMenu } from "../../../components/StyledMenu";
import ListItemText from "@mui/material/ListItemText";

const useStyles = makeStyles()((theme) => ({
  splitButtonBox: {
    display: "flex",
    flexDirection: "row",
    alignItems: "center",
    border: theme.borders.menuButton,
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
  buttonOuter: {
    height: "100%",
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-start",
    borderColor: `${theme.palette.contextMenuButton.border} !important`,
  },
  buttonLabel: {
    textTransform: "none",
    maxWidth: "85px",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
  },
}));

export type SplitButtonOption = {|
  text: string,
  selection?: () => void,
|};

type ContextMenuSplitButtonArgs = {|
  options: Array<SplitButtonOption>,
  icon: Node,
  loading?: boolean,
  disabledHelp?: string,
|};

export default function ContextMenuSplitButton({
  options,
  icon,
  loading = false,
  disabledHelp = "",
}: ContextMenuSplitButtonArgs): Node {
  const { classes } = useStyles();
  const [open, setOpen] = useState(false);
  const anchorRef = useRef(null);
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
    if (anchorRef.current && anchorRef.current.contains(event.target)) {
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
            <Box className={classes.iconBox} ml={0.5}>
              {loading ? (
                <FontAwesomeIcon icon="spinner" spin size="sm" />
              ) : (
                icon
              )}
            </Box>
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
                    className={classes.buttonOuter}
                    onClick={options[selectedIndex].selection}
                    disabled={disabledHelp !== ""}
                    color="contextMenuButton"
                    variant="text"
                  >
                    <span className={classes.buttonLabel}>
                      {options[selectedIndex].text}
                    </span>
                  </Button>
                  <Button
                    size="small"
                    aria-controls={open ? "split-button-menu" : null}
                    aria-expanded={open ? "true" : null}
                    aria-haspopup="menu"
                    aria-label="More selection options"
                    onClick={() => setOpen(!open)}
                    disabled={disabledHelp !== ""}
                    color="contextMenuButton"
                    variant="text"
                  >
                    <ArrowDropDownIcon />
                  </Button>
                </ButtonGroup>
                <StyledMenu
                  anchorEl={anchorRef.current}
                  open={open}
                  onClose={handleClose}
                >
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
