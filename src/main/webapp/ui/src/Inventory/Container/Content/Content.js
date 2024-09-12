//@flow

import React, { type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import ContentContextMenu from "./ContentContextMenu";
import useStores from "../../../stores/use-stores";
import SearchContext from "../../../stores/contexts/Search";
import SearchView from "../../Search/SearchView";
import Search from "../../Search/Search";
import { menuIDs } from "../../../util/menuIDs";
import Alert from "@mui/material/Alert";
import Grid from "@mui/material/Grid";
import ContainerModel from "../../../stores/models/ContainerModel";
import { isMac } from "../../../util/shortcuts";
import docLinks from "../../../assets/DocLinks";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";

const useStyles = makeStyles()((theme) => ({
  alert: {
    marginBottom: theme.spacing(1),
  },
  searchViewWrapper: {
    overflowX: "auto !important",
    overflow: "hidden",
    width: "100%",
  },
}));

function ImageContainerZoomHelpText() {
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  /*
   * If the user is on a small touchscreen device, like a phone, then they can
   * neither use the keyboard to zoom out nor the Panel Adjuster to view in
   * single column mode as they're always in single column mode.
   */
  if (uiStore.isTouchDevice && isSingleColumnLayout) return null;

  let zoomText: string = "Tip: Use Ctrl and the - key";
  try {
    // isMac relies on a deprecated browser API so the call may fail
    if (isMac()) zoomText = "Tip: Use Command and the - key";
  } catch (e) {
    return null;
  }
  zoomText = zoomText + " to zoom the page out to view more of the image.";

  const helpText = isSingleColumnLayout ? (
    zoomText
  ) : (
    <>
      {zoomText} The{" "}
      <a href={docLinks.panelAdjuster} rel="noreferrer" target="_blank">
        Panel Adjuster
      </a>{" "}
      can also be used to provide more room to fully display the image.
    </>
  );

  return <Alert severity="info">{helpText}</Alert>;
}

function _Content() {
  const { searchStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const search = activeResult.contentSearch;
  const fromCType = activeResult.cType.toUpperCase();
  const TABS = ["LIST", "TREE", "CARD"];
  if (fromCType === "IMAGE" || fromCType === "GRID") TABS.unshift(fromCType);

  const handleSearch = (query: string) => {
    const params = {
      query,
      parentGlobalId: activeResult.globalId,
    };
    search.staticFetcher.applySearchParams(params);
    search.dynamicFetcher.applySearchParams(params);
    search.cacheFetcher.applySearchParams(params);
    search.fetcher.performInitialSearch(params);
  };

  const locationsAlert =
    "Visual containers require an image with locations added to it. Click on 'Edit' (above) to complete the container's setup.";

  const { classes } = useStyles();
  return (
    <>
      {activeResult.cType === "IMAGE" &&
        !activeResult.loading &&
        (!activeResult.locationsImage || !activeResult.locationsCount) && (
          <Alert severity="warning" className={classes.alert}>
            {locationsAlert}
          </Alert>
        )}
      <SearchContext.Provider
        value={{
          search,
          scopedResult: activeResult,
          isChild: true,
          differentSearchForSettingActiveResult: searchStore.search,
        }}
      >
        <InnerSearchNavigationContext>
          <Grid container direction="column" spacing={1}>
            <Grid item>
              <Search handleSearch={handleSearch} TABS={TABS} size="small" />
            </Grid>
            {["GRID", "IMAGE"].includes(search.searchView) && (
              <Grid item>
                <ContentContextMenu />
              </Grid>
            )}
            <Grid item className={classes.searchViewWrapper}>
              <SearchView contextMenuId={menuIDs.RESULTS} />
            </Grid>
          </Grid>
        </InnerSearchNavigationContext>
      </SearchContext.Provider>
      {search.searchView === "IMAGE" && <ImageContainerZoomHelpText />}
    </>
  );
}

const Content: ComponentType<{||}> = observer(_Content);
export default Content;
