//@flow

import Button from "@mui/material/Button";
import React, { type Node } from "react";
import { styled } from "@mui/material/styles";
import ListItemText from "@mui/material/ListItemText";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import Avatar from "@mui/material/Avatar";

/**
 * This component is for displaying a button that has a large icon and a short
 * piece of explanatory text in addition to a label.
 */

type BigIconButtonArgs = {|
  label: string,
  icon: Node,
  explanatoryText: string,

  /**
   * When using the button inside of a HTMLLabelElement to trigger an invisible
   * HTMLInputElement with type "file", leave `onClick` undefined and set
   * `component` to "span". This will ensure that click events bubble up to the
   * HTMLLabelElement that should wrap the whole form field.
   */
  onClick?: () => void,
  component?: string,
|};

const CustomButton = styled(Button)(({ theme }) => ({
  textTransform: "none",
  boxShadow:
    "0px 3px 1px -2px rgba(0,0,0,0.2),0px 2px 2px 0px rgba(0,0,0,0.14),0px 1px 5px 0px rgba(0,0,0,0.12)",
  "& .MuiAvatar-root": {
    backgroundColor: theme.palette.primary.background,
    color: theme.palette.standardIcon.main,
  },
}));

export default function BigIconButton({
  label,
  icon,
  explanatoryText,
  onClick,
  component,
}: BigIconButtonArgs): Node {
  return (
    <CustomButton
      fullWidth
      color="primary"
      variant="outlined"
      onClick={onClick}
      component={component}
    >
      <ListItem disablePadding>
        <ListItemAvatar>
          <Avatar>{icon}</Avatar>
        </ListItemAvatar>
        <ListItemText primary={label} secondary={explanatoryText} />
      </ListItem>
    </CustomButton>
  );
}
