import React from "react";
import InfoBadge from "./InfoBadge";
import InfoCard from "./InfoCard";
import { makeStyles } from "tss-react/mui";
import { type Record } from "../../stores/definitions/Record";
import { observer } from "mobx-react-lite";

type NameWithBadgeArgs = {
  record: Record;
};

const useStyles = makeStyles<{ deleted: boolean }>()((theme, { deleted }) => ({
  name: {
    textDecorationLine: deleted ? "line-through" : "none",
    wordBreak: "break-all",
  },
}));

function NameWithBadge({ record }: NameWithBadgeArgs): React.ReactNode {
  const { classes } = useStyles({ deleted: record.deleted });
  return (
    <>
      <InfoBadge inline record={record}>
        <InfoCard record={record} />
      </InfoBadge>
      <span className={classes.name}>{record.name}</span>
    </>
  );
}

export default observer(NameWithBadge);
