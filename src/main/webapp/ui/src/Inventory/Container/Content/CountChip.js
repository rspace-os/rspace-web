// @flow

import React, { type Node } from "react";
import { makeStyles } from "tss-react/mui";
import Chip from "@mui/material/Chip";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import {
  type Container,
  type ContentSummary,
} from "../../../stores/definitions/Container";

type CountChipArgs = {|
  type: string,
  record: Container,
|};

const useStyles = makeStyles()((theme) => ({
  root: {
    marginLeft: theme.spacing(0.5),
  },
  icon: {
    marginLeft: `${theme.spacing(1)} !important`,
  },
}));

function getCount(type: string, cs: ContentSummary): number {
  if (type === "container") return cs.containerCount;
  if (type === "subSample") return cs.subSampleCount;
  throw new TypeError(
    'The string "type" can only be "container" or "subSample"'
  );
}

const CountChip = ({ type, record }: CountChipArgs): Node => {
  const { classes } = useStyles();
  if (!record.contentSummary.isAccessible) return null;
  const count = getCount(type, record.contentSummary.value);

  return (
    <Chip
      className={classes.root}
      label={count}
      size="small"
      icon={
        <span className={classes.icon}>
          <RecordTypeIcon
            record={{
              recordTypeLabel: type.toUpperCase(),
              iconName: type === "container" ? "container" : "sample",
            }}
          />
        </span>
      }
      variant="outlined"
    />
  );
};

export default CountChip;
