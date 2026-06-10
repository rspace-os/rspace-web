import React from "react";
import { observer } from "mobx-react-lite";
import { type SearchView as SearchViewType } from "../../../stores/definitions/Search";
import ContentContextMenu from "./ContentContextMenu";
import useStores from "../../../stores/use-stores";
import SearchContext from "../../../stores/contexts/Search";
import SearchViewComponent from "../../Search/SearchView";
import Search from "../../Search/Search";
import { menuIDs } from "../../../util/menuIDs";
import Alert from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import ContainerModel from "../../../stores/models/ContainerModel";
import { isMac } from "../../../util/shortcuts";
import docLinks from "../../../assets/DocLinks";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";

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
  } catch (_) {
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
  const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];
  if (fromCType === "IMAGE" || fromCType === "GRID")
    TABS.unshift(fromCType);

  const handleSearch = (query: string) => {
    const params = {
      query,
      parentGlobalId: activeResult.globalId,
    };
    search.staticFetcher.applySearchParams(params);
    search.dynamicFetcher.applySearchParams(params);
    search.cacheFetcher.applySearchParams(params);
    void search.fetcher.performInitialSearch(params);
  };

  const locationsAlert =
    "Visual containers require an image with locations added to it. Click on 'Edit' (above) to complete the container's setup.";

  return (
    <>
      {activeResult.cType === "IMAGE" &&
        !activeResult.loading &&
        (!activeResult.locationsImage || !activeResult.locationsCount) && (
          <Alert severity="warning" sx={{ mb: 1 }}>
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
          <Stack spacing={1}>
            <Search handleSearch={handleSearch} TABS={TABS} size="small" />
            {["GRID", "IMAGE"].includes(search.searchView) && (
              <ContentContextMenu />
            )}
            <Box sx={{ overflowX: "auto !important", overflow: "hidden", width: "100%" }}>
              <SearchViewComponent contextMenuId={menuIDs.RESULTS} />
            </Box>
          </Stack>
        </InnerSearchNavigationContext>
      </SearchContext.Provider>
      {search.searchView === "IMAGE" && <ImageContainerZoomHelpText />}
    </>
  );
}

const Content = observer(_Content);
export default Content;
