//@flow

import React, { type Node } from "react";
import { type State } from "../../../stores/definitions/InventoryRecord";
import { match } from "../../../util/Util";
import { cyan } from "@mui/material/colors";
import { useTheme } from "@mui/material/styles";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme, { color }) => ({
  box: {
    height: theme.spacing(3),
    top: ({ belowContextMenu }) => (belowContextMenu ? 40 : 0),
    position: "sticky",
    zIndex: 1000,
    borderTop: `${theme.spacing(0.5)} solid ${color}`,
    display: "flex",
    justifyContent: "flex-end",
  },
  tab: {
    backgroundColor: color,
    right: theme.spacing(1),
    color: "white",
    padding: theme.spacing(0.5, 1.5),
    borderBottomLeftRadius: theme.spacing(0.5),
    borderBottomRightRadius: theme.spacing(0.5),
    position: "absolute",
    top: theme.spacing(-0.5),
    fontWeight: 600,
  },
}));

type RecordStatusArgs = {|
  recordState: State,
  deleted: boolean,
|};

/*
 * It can be difficult to tell at a glance what the current state of a record
 * is. This component displays the most important information, passed as props,
 * using bold colours to make the information clear.
 */
export default function RecordStatus({
  recordState,
  deleted,
}: RecordStatusArgs): Node {
  const areEditing = recordState === "edit";
  const areCreating = recordState === "create";
  const theme = useTheme();
  const prefersMoreContrast = window.matchMedia(
    "(prefers-contrast: more)"
  ).matches;
  const color = match<void, string>([
    [() => areEditing, cyan[prefersMoreContrast ? 900 : 800]],
    [() => areCreating, cyan[prefersMoreContrast ? 900 : 800]],
    [() => deleted, theme.palette.deletedGrey],
    [() => true, "black"],
  ])();
  const { classes } = useStyles({
    color,
    belowContextMenu: !areEditing && !areCreating,
  });

  return areEditing || areCreating || deleted ? (
    <div className={classes.box}>
      <div className={classes.tab}>
        {areEditing && "EDITING"}
        {areCreating && "CREATING"}
        {deleted && "IN TRASH"}
      </div>
    </div>
  ) : null;
}
