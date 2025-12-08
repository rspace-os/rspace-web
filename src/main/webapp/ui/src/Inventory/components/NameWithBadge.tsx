import { observer } from "mobx-react-lite";
import type React from "react";
import { makeStyles } from "tss-react/mui";
import type { Record } from "../../stores/definitions/Record";
import InfoBadge from "./InfoBadge";
import InfoCard from "./InfoCard";

type NameWithBadgeArgs = {
    record: Record;
};

const useStyles = makeStyles<{ deleted: boolean }>()((_theme, { deleted }) => ({
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
