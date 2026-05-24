import { observer } from "mobx-react-lite";
import React from "react";
import Searchbar from "./components/Searchbar";
import SearchFeedback from "./components/SearchFeedback";
import { type SearchView } from "../../stores/definitions/Search";
import SearchDisplayControls from "./components/SearchDisplayControls";
import SearchParameterControls from "./components/SearchParameterControls";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Divider from "@mui/material/Divider";
import SearchParameterChips from "./components/SearchParameterChips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import docLinks from "../../assets/DocLinks";

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
        <HelpLinkIcon
          link={docLinks.search}
          title="Info on searching Inventory."
        />
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
