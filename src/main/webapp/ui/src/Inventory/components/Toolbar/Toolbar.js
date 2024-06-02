//@flow

import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import GlobalId from "../../../components/GlobalId";
import Alert from "@mui/material/Alert";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import ArrowBackIosIcon from "@mui/icons-material/ArrowBackIos";
import { observer } from "mobx-react-lite";
import React, { type Element, type Node, type ComponentType } from "react";
import { UserCancelledAction } from "../../../util/error";
import { type AllowedFormTypes } from "../../../stores/contexts/FormSections";
import RelativeBox from "../../../components/RelativeBox";
import StickyMenu from "../Stepper/StickyMenu";
import StickyStatus from "../StickyStatus";

const useStyles = makeStyles()((theme, { recordType }) => ({
  appBar: {
    backgroundColor: recordType
      ? theme.palette.record[recordType].bg
      : "white !important",
    color: recordType ? "white" : `${theme.palette.text.primary} !important`,
    border: recordType ? "none" : theme.borders.section,
  },
  toolbar: {
    display: "flex",
    justifyContent: "space-between",
    padding: `${theme.spacing(0.5)} !important`,
    minHeight: "auto",
    overflow: "hidden",
  },
  growTitle: {
    flexGrow: 1,
    minWidth: 0, // this is for the truncated 'title' ellipsis to work as expected
  },
  topRow: {
    margin: theme.spacing(0.5),
  },
  backButton: {
    padding: theme.spacing(1, 0.5, 1, 1.5),
    marginRight: theme.spacing(1),
  },
  backButtonIcon: {
    color: "white",
  },
  illustrationWrapper: {
    position: "relative",
    bottom: 0,
    alignSelf: "flex-end",
    marginRight: 10,
  },
  illustrationSpacer: {
    width: 90,
    right: 0,
  },
  typeLabel: {
    whiteSpace: "nowrap",
    paddingBottom: theme.spacing(0.25),
  },
}));

type CustomToolbarArgs = {|
  title: Node,
  record?: InventoryRecord,
  recordType: AllowedFormTypes,
  batch?: boolean,
  stickyAlert?: ?Element<typeof Alert>,
|};

/*
 * The top-most section of the right-hand side panel of the main Inventory UI,
 * which displays the name of the current record alongside visual elements.
 */
function CustomToolbar({
  title,
  record,
  recordType,
  batch,
  stickyAlert,
}: CustomToolbarArgs): Node {
  const { classes } = useStyles({ recordType });
  const {
    uiStore,
    searchStore: { search, activeResult },
  } = useStores();

  return (
    <AppBar position="sticky" className={classes.appBar} elevation={0}>
      <Toolbar variant="dense" className={classes.toolbar}>
        <Grid
          container
          wrap="nowrap"
          alignItems="center"
          className={classes.topRow}
        >
          {uiStore.isSingleColumnLayout && (
            <IconButton
              onClick={async () => {
                try {
                  await search.setActiveResult();
                  uiStore.setVisiblePanel("left");
                } catch (e) {
                  if (e instanceof UserCancelledAction) return;
                  throw e;
                }
              }}
              className={classes.backButton}
            >
              <ArrowBackIosIcon
                fontSize="small"
                data-test-id="backIcon"
                className={classes.backButtonIcon}
              />
            </IconButton>
          )}
          <Grid item className={classes.growTitle}>
            {title}
          </Grid>
          <Grid item className={classes.illustrationWrapper}>
            <div className={classes.illustrationSpacer}></div>
            {record && record.illustration}
          </Grid>
          {record && record.id !== null && (
            <Grid item>
              <Grid container direction="column" alignItems="center">
                <Typography
                  variant="caption"
                  component="span"
                  className={classes.typeLabel}
                >
                  {record.recordTypeLabel.toUpperCase()}
                </Typography>
                <GlobalId record={record} variant="white" />
              </Grid>
            </Grid>
          )}
        </Grid>
      </Toolbar>
      {activeResult && <StickyMenu stickyAlert={stickyAlert} />}
      <Box pb={0.25} />
      {(record ?? batch) && (
        <RelativeBox>
          <StickyStatus
            recordState={record?.state || "edit"}
            deleted={record?.deleted || false}
          />
        </RelativeBox>
      )}
    </AppBar>
  );
}

export default (observer(CustomToolbar): ComponentType<CustomToolbarArgs>);
