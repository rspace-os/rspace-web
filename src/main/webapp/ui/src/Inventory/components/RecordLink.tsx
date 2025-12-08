import Chip from "@mui/material/Chip";
import { emphasize } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import clsx from "clsx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { makeStyles } from "tss-react/mui";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import NavigateContext from "../../stores/contexts/Navigate";
import type { InventoryRecord } from "../../stores/definitions/InventoryRecord";
import useStores from "../../stores/use-stores";
import { preventEventBubbling, preventEventDefault } from "../../util/Util";

const useStyles = makeStyles()((theme) => ({
    root: {
        paddingLeft: theme.spacing(1),
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        backgroundColor: theme.palette.grey[200],
        color: theme.palette.grey[800],
        fontWeight: theme.typography.fontWeightRegular,
        "&:hover, &:focus": {
            backgroundColor: theme.palette.grey[300],
        },
        "&:focus-visible": {
            outline: `2px solid ${theme.palette.primary.main}`,
        },
        "&:active": {
            boxShadow: theme.shadows[1],
            backgroundColor: emphasize(theme.palette.grey[300], 0.12),
        },
        transitionDuration: "500ms",
    },
    overflowRoot: {
        wordBreak: "break-word",
        height: "auto",
        padding: theme.spacing(0.5, 1),
    },
    overflowLabel: {
        whiteSpace: "break-spaces",
    },
    static: {
        paddingLeft: theme.spacing(1),
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        backgroundColor: theme.palette.grey[300],
        color: theme.palette.grey[800],
        fontWeight: theme.typography.fontWeightRegular,
    },
    withoutIcon: {
        paddingRight: theme.spacing(2),
    },
}));

type RecordLinkArgs = {
    record: InventoryRecord;
    overflow?: boolean;
    newTab?: boolean;
};

export const RecordLink = observer(({ record, overflow = false }: RecordLinkArgs): React.ReactNode => {
    const { classes } = useStyles();
    const { trackingStore, uiStore } = useStores();
    const { useNavigate } = useContext(NavigateContext);
    const navigate = useNavigate();

    return (
        <Chip
            size="small"
            classes={{
                root: clsx(classes.root, overflow && classes.overflowRoot),
                label: clsx(overflow && classes.overflowLabel),
            }}
            component="a"
            href={record.permalinkURL || undefined}
            label={record.recordLinkLabel}
            icon={<RecordTypeIcon record={record} />}
            onClick={preventEventBubbling(
                preventEventDefault((_: React.MouseEvent) => {
                    if (record.permalinkURL) {
                        navigate(record.permalinkURL);
                        trackingStore.trackEvent("BreadcrumbClicked");
                        uiStore.setVisiblePanel(record.showRecordOnNavigate ? "right" : "left");
                    }
                }),
            )}
        />
    );
});

type TopLinkArgs = {
    overflow?: boolean;
};

export const TopLink = observer(({ overflow = false }: TopLinkArgs): React.ReactNode => {
    const { classes } = useStyles();
    const { searchStore, trackingStore } = useStores();
    const { useNavigate } = useContext(NavigateContext);
    const navigate = useNavigate();

    const containersRoot = `/inventory/search?${searchStore.fetcher
        .generateNewQuery({ resultType: "CONTAINER" })
        .toString()}`;

    const toTopContainers = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        navigate(containersRoot);
        trackingStore.trackEvent("BreadcrumbClicked");
    };

    return (
        <Typography variant="body1">
            <Chip
                size="small"
                classes={{
                    root: clsx(classes.root, overflow && classes.overflowRoot),
                    label: clsx(classes.withoutIcon, overflow && classes.overflowLabel),
                }}
                component="span"
                label="Containers"
                onClick={toTopContainers}
            />
        </Typography>
    );
});

type CurrentRecordArgs = {
    record: InventoryRecord;
    overflow?: boolean;
};

export const CurrentRecord = observer(({ record, overflow = false }: CurrentRecordArgs): React.ReactNode => {
    const { classes } = useStyles();

    return (
        <Typography variant="body1">
            <Chip
                size="small"
                classes={{
                    root: clsx(classes.static, overflow && classes.overflowRoot),
                    label: clsx(overflow && classes.overflowLabel),
                }}
                clickable={false}
                component="span"
                label={record.recordLinkLabel}
                icon={<RecordTypeIcon record={record} />}
            />
        </Typography>
    );
});

export function InTrash(): React.ReactNode {
    const { classes } = useStyles();

    return (
        <Typography variant="body1">
            <Chip
                size="small"
                classes={{
                    root: classes.static,
                    label: classes.withoutIcon,
                }}
                clickable={false}
                component="span"
                label="In Trash"
            />
        </Typography>
    );
}
