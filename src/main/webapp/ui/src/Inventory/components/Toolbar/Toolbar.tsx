import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import GlobalId from "../../../components/GlobalId";
import type Alert from "@mui/material/Alert";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import ArrowBackIosIcon from "@mui/icons-material/ArrowBackIos";
import { observer } from "mobx-react-lite";
import React from "react";
import { UserCancelledAction } from "../../../util/error";
import { type AllowedFormTypes } from "../../../stores/contexts/FormSections";
import RelativeBox from "../../../components/RelativeBox";
import StickyMenu from "../Stepper/StickyMenu";
import StickyStatus from "../StickyStatus";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

const useStyles = makeStyles<{
  recordType?: AllowedFormTypes;
}>()((theme, { recordType }) => ({
  appBar: {
    backgroundColor:
      recordType && theme.palette.record[recordType]
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
    backgroundColor: "unset !important",
    color: "inherit !important",
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
    color: "inherit",
    whiteSpace: "nowrap",
    paddingBottom: theme.spacing(0.25),
  },
}));

type CustomToolbarArgs = {
  title: React.ReactNode;
  record?: InventoryRecord;
  recordType: AllowedFormTypes;
  batch?: boolean;
  stickyAlert?: React.ReactElement<typeof Alert> | null;
};

/**
 * The top-most section of the right-hand side panel of the main Inventory UI,
 * which displays the name of the current record alongside visual elements.
 */
function CustomToolbar({
  title,
  record,
  recordType,
  batch,
  stickyAlert,
}: CustomToolbarArgs): React.ReactNode {
  const { classes } = useStyles({ recordType });
  const {
    uiStore,
    searchStore: { search, activeResult },
  } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const handleBackClick = () => {
    try {
      // Using void to handle the promise without awaiting it
      void (async () => {
        await search.setActiveResult();
        uiStore.setVisiblePanel("left");
      })();
    } catch (e) {
      if (e instanceof UserCancelledAction) return;
      throw e;
    }
  };

  return (
    <AppBar position="sticky" className={classes.appBar} elevation={0}>
      <Toolbar variant="dense" className={classes.toolbar}>
        <Grid
          container
          wrap="nowrap"
          alignItems="center"
          className={classes.topRow}
        >
          {isSingleColumnLayout && (
            <IconButton
              onClick={handleBackClick}
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
      {(record !== undefined || batch === true) && (
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

export default observer(CustomToolbar);
