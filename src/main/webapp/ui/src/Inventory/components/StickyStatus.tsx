import RecordStatus from "./Toolbar/RecordStatus";
import React from "react";
import { makeStyles } from "tss-react/mui";
import { type State } from "../../stores/definitions/InventoryRecord";

const useStyles = makeStyles()((theme) => ({
  stickyStatus: {
    position: "absolute",
    height: "100%",
    width: "100%",
    top: 0,
    pointerEvents: "none",
    paddingBottom: theme.spacing(0.5),
  },
}));

type StickyStatusArgs = {
  recordState: State;
  deleted: boolean;
};

/*
 * RecordStatus, with the additional styles to position it absolutely
 */
export default function StickyStatus({
  recordState,
  deleted,
}: StickyStatusArgs): React.ReactNode {
  const { classes } = useStyles();

  return ["create", "edit"].includes(recordState) || deleted ? (
    <div className={classes.stickyStatus}>
      <RecordStatus recordState={recordState} deleted={deleted} />
    </div>
  ) : null;
}
