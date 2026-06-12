import Chip, { chipClasses } from "@mui/material/Chip";
import { emphasize, type Theme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import { type MouseEvent, useContext } from "react";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "@/stores/definitions/InventoryRecord";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import NavigateContext from "../../stores/contexts/Navigate";
import useStores from "../../stores/use-stores";

type OverflowProps = {
  overflow?: boolean;
};

type ChipSxOptions = OverflowProps & {
  withoutIcon?: boolean;
  clickable?: boolean;
};

type RecordChipProps = OverflowProps & {
  record: InventoryRecord;
};

const interactiveChipSx =
  ({ overflow = false, withoutIcon = false, clickable = false }: ChipSxOptions = {}) =>
  (theme: Theme) => ({
    ...(overflow
      ? {
          padding: theme.spacing(0.5, 1),
          wordBreak: "break-word",
          height: "auto",
        }
      : {
          paddingLeft: theme.spacing(1),
        }),
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    backgroundColor: theme.palette.grey[200],
    color: theme.palette.grey[800],
    "&&": {
      backgroundColor: `${theme.palette.grey[200]} !important`,
      color: `${theme.palette.grey[800]} !important`,
      "&:hover, &:focus": {
        backgroundColor: `${theme.palette.grey[300]} !important`,
      },
      "&:active": {
        boxShadow: theme.shadows[1],
        backgroundColor: `${emphasize(theme.palette.grey[300], 0.12)} !important`,
      },
    },
    ...(clickable
      ? {
          cursor: "pointer",
        }
      : {}),
    fontWeight: theme.typography.fontWeightRegular ?? 400,
    transitionDuration: "500ms",
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
    [`& .${chipClasses.label}`]: {
      ...(overflow
        ? {
            whiteSpace: "break-spaces",
          }
        : {}),
      ...(withoutIcon
        ? {
            paddingRight: theme.spacing(2),
          }
        : {}),
      color: `${theme.palette.grey[800]} !important`,
    },
  });

const staticChipSx =
  ({ overflow = false, withoutIcon = false }: ChipSxOptions = {}) =>
  (theme: Theme) => ({
    ...(overflow
      ? {
          padding: theme.spacing(0.5, 1),
          wordBreak: "break-word",
          height: "auto",
        }
      : {
          paddingLeft: theme.spacing(1),
        }),
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    backgroundColor: theme.palette.grey[300],
    color: theme.palette.grey[800],
    "&&": {
      backgroundColor: `${theme.palette.grey[300]} !important`,
      color: `${theme.palette.grey[800]} !important`,
    },
    fontWeight: theme.typography.fontWeightRegular ?? 400,
    [`& .${chipClasses.label}`]: {
      ...(overflow
        ? {
            whiteSpace: "break-spaces",
          }
        : {}),
      ...(withoutIcon
        ? {
            paddingRight: theme.spacing(2),
          }
        : {}),
      color: `${theme.palette.grey[800]} !important`,
    },
  });

type RecordLinkArgs = RecordChipProps & {
  newTab?: boolean;
  hideRecordTypeTooltip?: boolean;
  /**
   * Enable this to disable using `navigate()`. Used in workspace non-Reactified context where React Router is not available.
   */
  disableNavigationContext?: boolean;
};

export const RecordLink = observer(
  ({
    record,
    overflow = false,
    newTab = false,
    hideRecordTypeTooltip = false,
    disableNavigationContext = false,
  }: RecordLinkArgs) => {
    const { trackingStore, uiStore } = useStores();
    const { useNavigate } = useContext(NavigateContext);
    const navigate = useNavigate();

    const onClick = (event: MouseEvent) => {
      if (!record.permalinkURL) {
        return;
      }

      if (disableNavigationContext) {
        return;
      }

      event.stopPropagation();
      event.preventDefault();
      navigate(record.permalinkURL);
      trackingStore.trackEvent("BreadcrumbClicked");
      uiStore.setVisiblePanel(record.showRecordOnNavigate ? "right" : "left");
    };

    return (
      <Chip
        size="small"
        sx={interactiveChipSx({
          overflow,
          clickable: Boolean(record.permalinkURL),
        })}
        component="a"
        href={record.permalinkURL || undefined}
        label={record.recordLinkLabel}
        target={newTab ? "_blank" : undefined}
        icon={<RecordTypeIcon record={record} disableTooltip={hideRecordTypeTooltip} />}
        onClick={onClick}
      />
    );
  },
);

export const TopLink = observer(({ overflow = false }: OverflowProps) => {
  const { searchStore, trackingStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const containersRoot = `/inventory/search?${searchStore.fetcher
    .generateNewQuery({ resultType: "CONTAINER" })
    .toString()}`;

  const toTopContainers = (e: MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigate(containersRoot);
    trackingStore.trackEvent("BreadcrumbClicked");
  };

  return (
    <Typography variant="body1">
      <Chip
        size="small"
        sx={interactiveChipSx({ overflow, withoutIcon: true })}
        component="span"
        label="Containers"
        onClick={toTopContainers}
      />
    </Typography>
  );
});

export const CurrentRecord = observer(({ record, overflow = false }: RecordChipProps) => {
  return (
    <Typography variant="body1">
      <Chip
        size="small"
        sx={staticChipSx({ overflow })}
        clickable={false}
        component="span"
        label={record.recordLinkLabel}
        icon={<RecordTypeIcon record={record} />}
      />
    </Typography>
  );
});

export const InTrash = () => (
  <Typography variant="body1">
    <Chip size="small" sx={staticChipSx({ withoutIcon: true })} clickable={false} component="span" label="In Trash" />
  </Typography>
);
