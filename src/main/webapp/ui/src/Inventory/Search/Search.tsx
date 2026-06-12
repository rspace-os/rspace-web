import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import docLinks from "../../assets/DocLinks";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import type { SearchView } from "../../stores/definitions/Search";
import Searchbar from "./components/Searchbar";
import SearchDisplayControls from "./components/SearchDisplayControls";
import SearchFeedback from "./components/SearchFeedback";
import SearchParameterChips from "./components/SearchParameterChips";
import SearchParameterControls from "./components/SearchParameterControls";

type MainSearchArgs = {
  size?: "small" | "default";
  TABS?: Array<SearchView>;
  handleSearch: (query: string) => void;
  searchbarAdornment?: React.ReactNode;
};

function MainSearch({
  size = "default",
  handleSearch,
  TABS = ["LIST", "TREE", "CARD"],
  searchbarAdornment,
}: MainSearchArgs): React.ReactNode {
  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <Box sx={{ flexGrow: 1 }}>
          <Searchbar handleSearch={handleSearch} />
        </Box>
        <HelpLinkIcon link={docLinks.search} title="Info on searching Inventory." />
        {size === "small" && <SearchDisplayControls TABS={TABS} />}
        {Boolean(searchbarAdornment) && searchbarAdornment}
      </Stack>
      <Box sx={{ pt: 0 }}>
        <SearchParameterControls />
      </Box>
      <Box sx={{ maxWidth: "100% !important" }}>
        <SearchParameterChips />
      </Box>
      {size === "default" && (
        <>
          <Divider orientation="horizontal" sx={{ my: 0.75 }} />
          <Stack direction="row" spacing={1}>
            <Box sx={{ flexGrow: 1 }}>
              <SearchFeedback />
            </Box>
            <SearchDisplayControls TABS={TABS} />
          </Stack>
        </>
      )}
    </Stack>
  );
}

export default observer(MainSearch);
