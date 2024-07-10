// @flow

import ContextMenu, { type ContextMenuArgs } from "./ContextMenu";
import ContextMenuButton from "./ContextMenuButton";
import ContextMenuSplitButton from "./ContextMenuSplitButton";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import GridItem from "./ContextMenuGridItem";
import { makeStyles } from "tss-react/mui";
import Result from "../../../stores/models/Result";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
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

type ExtendedContextMenuArgs = {|
  prefixActions: Array<
    | {|
        disabledHelp: string,
        icon: Node,
        key: string,
        options: Array<SplitButtonOption>,
      |}
    | {|
        disabledHelp: string,
        icon: Node,
        key: string,
        label: string,
        variant?: string,
        onClick?: (Event) => void,
        active?: boolean,
      |}
  >,
  selectedResults: Array<InventoryRecord>,
  onSelectOptions?: Array<SplitButtonOption>,
  menuID: string,
  basketSearch?: boolean,
  ...$Rest<
    ContextMenuArgs,
    {|
      selectedResults: Array<InventoryRecord>,
      menuID: string,
      onSelectOptions?: Array<SplitButtonOption>,
      basketSearch: boolean,
      paddingTop: boolean,
    |}
  >,
|};

function ExtendedContextMenu({
  prefixActions,
  selectedResults,
  onSelectOptions,
  menuID,
  basketSearch,
  ...rest
}: ExtendedContextMenuArgs): Node {
  const { classes } = useStyles();
  return (
    <Grid container className={classes.mainContainer}>
      <Grid item>
        <Grid container>
          {/* Note: in the "content" ContextMenu the Select action is included in prefixActions */}
          {prefixActions.map(
            (
              action:
                | {|
                    disabledHelp: string,
                    icon: Node,
                    key: string,
                    options: Array<SplitButtonOption>,
                  |}
                | {|
                    disabledHelp: string,
                    icon: Node,
                    key: string,
                    label: string,
                    variant?: string,
                    onClick?: (Event) => void,
                    active?: boolean,
                  |}
            ) => (
              <GridItem key={action.key}>
                {/* conditional rendering based on: options or onClick */}
                {action.options ? (
                  <ContextMenuSplitButton {...action} />
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

export default (observer(
  ExtendedContextMenu
): ComponentType<ExtendedContextMenuArgs>);
