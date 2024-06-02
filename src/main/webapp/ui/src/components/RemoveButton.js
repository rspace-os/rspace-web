//@flow

import React, { type Node } from "react";
import { makeStyles } from "tss-react/mui";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import ClearIcon from "@mui/icons-material/Clear";

type RemoveButtonArgs = {|
  onClick?: () => void,
  title?: string,
  disabled?: boolean,
|};

const useStyles = makeStyles()((theme) => ({
  removeIcon: {
    "&:hover": {
      color: theme.palette.warningRed,
    },
  },
}));

const RemoveButton = ({
  onClick,
  title = "Delete",
  disabled = false,
}: RemoveButtonArgs): Node => {
  const { classes } = useStyles();
  return (
    <IconButtonWithTooltip
      title={title}
      icon={<ClearIcon />}
      size="small"
      className={classes.removeIcon}
      onClick={onClick}
      disabled={disabled}
    />
  );
};

export default RemoveButton;
