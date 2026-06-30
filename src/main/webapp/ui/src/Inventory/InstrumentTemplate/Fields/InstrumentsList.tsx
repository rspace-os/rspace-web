import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import SearchContext from "../../../stores/contexts/Search";
import type { SearchView as SearchViewType } from "../../../stores/definitions/Search";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import InnerSearchNavigationContext from "../../components/InnerSearchNavigationContext";
import Search from "../../Search/Search";
import SearchView from "../../Search/SearchView";

const TABS: SearchViewType[] = ["LIST", "TREE", "CARD"];

function InstrumentsList(): React.ReactNode {
  const {
    searchStore: { activeResult, search },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");

  const instrumentsSearch = () => activeResult.search;

  const handleSearch = (query: string) => {
    void instrumentsSearch().fetcher.performInitialSearch({
      query,
      parentGlobalId: activeResult.globalId,
    });
  };

  return (
    <SearchContext.Provider
      value={{
        scopedResult: activeResult,
        search: instrumentsSearch(),
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

export default observer(InstrumentsList);
