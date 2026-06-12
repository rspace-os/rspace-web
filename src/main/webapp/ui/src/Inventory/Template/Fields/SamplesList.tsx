import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import SearchContext from "../../../stores/contexts/Search";
// biome-ignore lint/style/useImportType: initial biome migration
import { type SearchView as SearchViewType } from "../../../stores/definitions/Search";
import TemplateModel from "../../../stores/models/TemplateModel";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import Search from "../../Search/Search";
import SearchView from "../../Search/SearchView";

const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];

function SamplesList(): React.ReactNode {
  const {
    searchStore: { activeResult, search },
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");

  const samplesSearch = () => activeResult.search;

  const handleSearch = (query: string) => {
    void samplesSearch().fetcher.performInitialSearch({
      query,
      parentGlobalId: activeResult.globalId,
    });
  };

  return (
    <SearchContext.Provider
      value={{
        scopedResult: activeResult,
        search: samplesSearch(),
        isChild: true,
        differentSearchForSettingActiveResult: search,
      }}
    >
      <InnerSearchNavigationContext>
        <Stack spacing={1}>
          <Search handleSearch={handleSearch} TABS={TABS} size="small" />
          <SearchView contextMenuId={menuIDs.RESULTS} />
        </Stack>
      </InnerSearchNavigationContext>
    </SearchContext.Provider>
  );
}

export default observer(SamplesList);
