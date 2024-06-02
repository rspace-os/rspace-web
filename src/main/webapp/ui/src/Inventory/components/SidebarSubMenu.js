// @flow

import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import React, { useState, type Node, useId } from "react";
import { makeStyles } from "tss-react/mui";
import { toTitleCase, match, capitalise } from "../../util/Util";
import { StyledMenu, StyledMenuItem } from "../../components/StyledMenu";
import RecordTypeIcon from "../../components/RecordTypeIcon";

export type SidebarSubMenuRecordTypes =
  | "CONTAINER"
  | "SAMPLE"
  | "TEMPLATE"
  | "SUBSAMPLE";

const Icon = ({ type }: { type: SidebarSubMenuRecordTypes }) => (
  <RecordTypeIcon
    record={{
      recordTypeLabel: "",
      iconName: match<SidebarSubMenuRecordTypes, string>([
        [(t) => t === "CONTAINER", "container"],
        [(t) => t === "SAMPLE", "sample"],
        [(t) => t === "TEMPLATE", "template"],
        [(t) => t === "SUBSAMPLE", "subsample"],
      ])(type),
    }}
    color=""
  />
);

const useStyles = makeStyles()((theme) => ({
  button: {
    paddingLeft: theme.spacing(3),
    borderTopRightRadius: theme.spacing(3),
    borderBottomRightRadius: theme.spacing(3),
    color: theme.palette.primary.main,
  },
  buttonIcon: {
    color: theme.palette.primary.main,
  },
}));

type SidebarSubMenuArgs = {|
  buttonIcon: Node,
  buttonLabel: string,
  onClick: (SidebarSubMenuRecordTypes) => void,
  selected?: boolean,
  types: Array<SidebarSubMenuRecordTypes>,
  plural?: boolean,
|};

export default function SidebarSubMenu({
  buttonIcon,
  buttonLabel,
  onClick,
  selected = false,
  types,
  plural = false,
}: SidebarSubMenuArgs): Node {
  const [anchorEl, setAnchorEl] = useState<?EventTarget>(null);
  const { classes } = useStyles();

  const handleClick = (event: Event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleCreate = (recordType: SidebarSubMenuRecordTypes) => {
    handleClose();
    onClick(recordType);
  };

  const controls = useId();
  return (
    <div>
      <ListItem
        button
        className={classes.button}
        aria-controls={anchorEl ? controls : null}
        aria-haspopup="true"
        variant="contained"
        color="primary"
        onClick={handleClick}
        selected={selected}
        data-test-id={`${buttonLabel}NavButton`}
      >
        <ListItemIcon className={classes.buttonIcon}>{buttonIcon}</ListItemIcon>
        <ListItemText primary={buttonLabel} />
      </ListItem>
      <StyledMenu
        id={controls}
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleClose}
      >
        {types.map((type: SidebarSubMenuRecordTypes, i: number) => (
          <StyledMenuItem
            key={i}
            onClick={() => handleCreate(type)}
            data-test-id={`Create${capitalise(type)}NavButton`}
          >
            <ListItemIcon>
              <Icon type={type} />
            </ListItemIcon>
            <ListItemText
              primary={`${toTitleCase(type)}${plural ? "s" : ""}`}
            />
          </StyledMenuItem>
        ))}
      </StyledMenu>
    </div>
  );
}
