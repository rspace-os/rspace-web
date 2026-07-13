import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import SearchContext from "../../../stores/contexts/Search";
import type { SearchView as SearchViewType } from "../../../stores/definitions/Search";
import ContainerModel from "../../../stores/models/ContainerModel";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import { isMac } from "../../../util/shortcuts";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import Search from "../../Search/Search";
import SearchViewComponent from "../../Search/SearchView";
import ContentContextMenu from "./ContentContextMenu";

function ImageContainerZoomHelpText() {
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  /*
   * If the user is on a small touchscreen device, like a phone, then they can
   * neither use the keyboard to zoom out nor the Panel Adjuster to view in
   * single column mode as they're always in single column mode.
   */
  if (uiStore.isTouchDevice && isSingleColumnLayout) return null;

  return (
    <Alert severity="info">
      <TransRichText
        i18nKey={isMac() ? "inventory:container.content.zoom.macTip" : "inventory:container.content.zoom.ctrlTip"}
      />
      {!isSingleColumnLayout && (
        <>
          {" "}
          <TransRichText i18nKey="inventory:container.content.zoom.panelAdjuster" />
        </>
      )}
    </Alert>
  );
}

function _Content() {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!(activeResult instanceof ContainerModel)) throw new Error("ActiveResult must be a Container");
  const search = activeResult.contentSearch;
  const fromCType = activeResult.cType.toUpperCase();
  const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];
  if (fromCType === "IMAGE" || fromCType === "GRID") TABS.unshift(fromCType);

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

  return (
    <>
      {activeResult.cType === "IMAGE" &&
        !activeResult.loading &&
        (!activeResult.locationsImage || !activeResult.locationsCount) && (
          <Alert severity="warning" sx={{ mb: 1 }}>
            {t("container.content.locationsAlert")}
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
            {["GRID", "IMAGE"].includes(search.searchView) && <ContentContextMenu />}
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
