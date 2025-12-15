import React, {
  useState,
  useLayoutEffect,
  useRef,
  type RefObject,
} from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import { makeStyles } from "tss-react/mui";
import GridItem from "./ContextMenuGridItem";
import contextActions from "./ContextActions";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { StyledMenu } from "../../../components/StyledMenu";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";

const useStyles = makeStyles<{
  paddingTop: boolean;
}>()((theme, { paddingTop }) => ({
  spaceForButtons: {
    overflowY: "hidden",
    maxHeight: theme.spacing(4 + (paddingTop ? 1 : 0)),
    maxWidth: `calc(100% - 40px)`, // 40px is width of overflow menu button
    paddingTop: paddingTop ? theme.spacing(1) : "initial",
  },
  spacer: {
    flexGrow: 1,
  },
  moreButtonWrapper: {
    paddingTop: paddingTop ? theme.spacing(1) : "initial",
  },
  moreButton: {
    padding: theme.spacing(0.75),
  },
  alert: {
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    paddingTop: theme.spacing(0),
    paddingBottom: theme.spacing(0),
  },
}));

type Buttons = Array<{
  inOverflow: boolean;
  ref: RefObject<HTMLElement>;
}>;

type ContextAction = {
  hidden?: boolean;
  component: React.ReactNode;
};

export type ContextMenuArgs = {
  selectedResults: Array<InventoryRecord>;
  forceDisabled?: string;
  onSelectOptions?: Array<SplitButtonOption>;
  menuID: string;
  paddingTop: boolean;
  basketSearch: boolean;
};

function ContextMenu({
  selectedResults = [],
  forceDisabled = "",
  onSelectOptions,
  menuID,
  paddingTop,
  basketSearch,
}: ContextMenuArgs): React.ReactNode {
  const { classes } = useStyles({ paddingTop });
  const MENU_PADDING = 3;

  const anySelected = selectedResults.length > 0;
  const mixedSelectedStatus = // affecting all except select
    selectedResults.some((r) => r.deleted) &&
    selectedResults.some((r) => !r.deleted);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  type ContextActionsFn = (type: "button" | "menuitem") => ContextAction[];

  const actions: ContextActionsFn = contextActions({
    selectedResults,
    mixedSelectedStatus,
    forceDisabled,
    closeMenu: () => setAnchorEl(null),
    onSelectOptions,
    menuID,
    basketSearch,
  });

  const spaceForButtons = useRef<HTMLElement | null>(null);

  const initialButtons: Buttons = actions("button").map(() => ({
    inOverflow: false,
    ref: React.createRef<HTMLElement>(),
  }));
  const [buttons, setButtons] = useState<Buttons>(initialButtons);

  const moreButton = useRef<HTMLElement | null>(null);

  const widthOfRef = (ref: { current: HTMLElement | null }): number =>
    ref.current?.offsetWidth ?? 0;

  const calculateWhatIsInOverflowMenu = () => {
    const newButtons = buttons.map((b) => ({
      ...b,
      inOverflow: false,
    }));
    setButtons([...newButtons]);
    setTimeout(() => {
      const buttonWidths = actions("button").map((a, i) =>
        widthOfRef(buttons[i].ref)
      );
      const availableSpace = widthOfRef(spaceForButtons);
      const cumulativeWidth =
        buttonWidths.reduce((totalWidth, buttonWidth, i) => {
          totalWidth += Math.ceil(
            buttonWidth + (buttonWidth > 0 ? MENU_PADDING : 0)
          );
          newButtons[i].inOverflow = totalWidth >= availableSpace;
          return totalWidth;
        }, 0) ?? 0;

      // close overflow menu if empty
      if (cumulativeWidth < availableSpace) {
        setAnchorEl(null);
      }

      // update buttons state (re-render)
      setButtons(newButtons);
    }, 0);
  };

  const containerObserver = useRef(
    new ResizeObserver(calculateWhatIsInOverflowMenu)
  );

  useLayoutEffect(() => {
    const currentSpaceForButtons = spaceForButtons.current;
    const currentObserver = containerObserver.current;

    if (currentSpaceForButtons) {
      currentObserver.observe(currentSpaceForButtons);
    }
    return () => {
      if (currentSpaceForButtons) {
        currentObserver.unobserve(currentSpaceForButtons);
      }
    };
  }, [selectedResults]);

  /* making this variable as more cases could be added */
  const showMenuAlert = mixedSelectedStatus;
  const alertMessage =
    mixedSelectedStatus &&
    `Please select only 'Current' or 'In Trash' items to view more actions`;

  const MenuAlert = () => (
    <Alert severity="warning" className={classes.alert}>
      {alertMessage}
    </Alert>
  );

  return (
    <div>
      {anySelected && (
        <Grid container wrap="nowrap">
          <Grid
            item
            ref={spaceForButtons as React.RefObject<HTMLDivElement>}
            className={classes.spaceForButtons}
          >
            <Grid container>
              {actions("button").map((action: ContextAction, i) =>
                !action.hidden ? (
                  <GridItem key={i}>
                    <Box
                      ref={buttons[i].ref as React.RefObject<HTMLDivElement>}
                    >
                      {action.component}
                    </Box>
                  </GridItem>
                ) : null
              )}
            </Grid>
          </Grid>
          <Grid item className={classes.spacer}></Grid>
          <GridItem className={classes.moreButtonWrapper}>
            <Box ref={moreButton as React.RefObject<HTMLDivElement>}>
              <IconButtonWithTooltip
                title="More actions"
                icon={<MoreHorizIcon />}
                aria-haspopup="menu"
                className={classes.moreButton}
                size="medium"
                onClick={(event) => setAnchorEl(event.currentTarget)}
                disabled={buttons.every((b) => !b.inOverflow)}
              />
            </Box>
            <StyledMenu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={() => setAnchorEl(null)}
              disableAutoFocusItem={true}
            >
              {actions("menuitem").map((action: ContextAction, i) =>
                !action.hidden && buttons[i]?.inOverflow
                  ? action.component
                  : null
              )}
            </StyledMenu>
          </GridItem>
        </Grid>
      )}
      {showMenuAlert && <MenuAlert />}
    </div>
  );
}

export default observer(ContextMenu);
