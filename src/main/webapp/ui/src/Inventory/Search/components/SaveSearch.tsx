import BookmarkBorderOutlinedIcon from "@mui/icons-material/BookmarkBorderOutlined";
import type { SxProps, Theme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import NameDialog from "./NameDialog";

type SaveSearchArgs = {
  sx?: SxProps<Theme>;
};

function SaveSearch({ sx }: SaveSearchArgs): React.ReactNode {
  const { search } = useContext(SearchContext);
  const { searchStore } = useStores();

  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");

  const handleOpen = () => {
    /*
     * `||` rather than `??` because query is empty string, not null, when
     * not set
     */
    setName(search.fetcher.query || "New saved search");
    setOpen(true);
  };

  return (
    <>
      {!search.fetcher.permalink && !search.fetcher.error && !searchStore.searchIsSaved ? (
        <IconButtonWithTooltip
          title="Save search"
          size="small"
          data-test-id="save-search"
          onClick={handleOpen}
          icon={<BookmarkBorderOutlinedIcon fontSize="small" />}
          sx={sx}
        />
      ) : null}
      <NameDialog
        open={open}
        setOpen={setOpen}
        name={name}
        setName={setName}
        existingNames={searchStore.savedSearches.map((s) => s.name)}
        onChange={() => {
          searchStore.saveSearch(name);
        }}
      />
    </>
  );
}

export default observer(SaveSearch);
