import ContextMenu, { type ContextMenuArgs } from "./ContextMenu";
import ContextMenuButton from "./ContextMenuButton";
import ContextMenuSplitButton, {
  type SplitButtonOption,
} from "./ContextMenuSplitButton";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import React from "react";
import { observer } from "mobx-react-lite";
import GridItem from "./ContextMenuGridItem";
import { makeStyles } from "tss-react/mui";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

const useStyles = makeStyles()(() => ({
  mainContainer: {
    flexWrap: "nowrap",
  },
  contextMenuWrapper: {
    flexGrow: 1,
  },
  divider: {
    margin: "0px 3px",
  },
}));

type ExtendedContextMenuArgs = {
  prefixActions: Array<
    | {
        disabledHelp: string;
        icon: React.ReactElement;
        key: string;
        options: Array<SplitButtonOption>;
      }
    | {
        disabledHelp: string;
        icon: React.ReactElement;
        key: string;
        label: string;
        variant?: "default" | "filled";
        onClick?: (event: React.MouseEvent<HTMLDivElement>) => void;
        active?: boolean;
      }
  >;
  selectedResults: Array<InventoryRecord>;
  onSelectOptions?: Array<SplitButtonOption>;
  menuID: string;
  basketSearch?: boolean;
} & Omit<
  ContextMenuArgs,
  | "selectedResults"
  | "menuID"
  | "onSelectOptions"
  | "basketSearch"
  | "paddingTop"
>;

function ExtendedContextMenu({
  prefixActions,
  selectedResults,
  onSelectOptions,
  menuID,
  basketSearch,
  ...rest
}: ExtendedContextMenuArgs): React.ReactNode {
  const { classes } = useStyles();
  return (
    <Grid container className={classes.mainContainer}>
      <Grid item>
        <Grid container>
          {/* Note: in the "content" ContextMenu the Select action is included in prefixActions */}
          {prefixActions.map(
            (
              action:
                | {
                    disabledHelp: string;
                    icon: React.ReactElement;
                    key: string;
                    options: Array<SplitButtonOption>;
                  }
                | {
                    disabledHelp: string;
                    icon: React.ReactElement;
                    key: string;
                    label: string;
                    variant?: "default" | "filled";
                    onClick?: (event: React.MouseEvent<HTMLDivElement>) => void;
                    active?: boolean;
                  }
            ) => (
              <GridItem key={action.key}>
                {/* conditional rendering based on: options or onClick */}
                {"options" in action ? (
                  <ContextMenuSplitButton
                    {...(action as {
                      disabledHelp: string;
                      icon: React.ReactElement;
                      key: string;
                      options: Array<SplitButtonOption>;
                    })}
                  />
                ) : (
                  <ContextMenuButton {...action} />
                )}
              </GridItem>
            )
          )}
        </Grid>
      </Grid>
      {selectedResults.length > 0 && (
        <>
          <Grid item>
            <Divider orientation="vertical" className={classes.divider} />
          </Grid>
          {/* NB: in other context menus ("results" and "stepper"), the Select action is part of the ContextMenu actions */}
          <Grid item className={classes.contextMenuWrapper}>
            <ContextMenu
              selectedResults={selectedResults}
              menuID={menuID}
              onSelectOptions={onSelectOptions}
              basketSearch={basketSearch ?? false}
              paddingTop={false}
              {...rest}
            />
          </Grid>
        </>
      )}
    </Grid>
  );
}

export default observer(ExtendedContextMenu);
