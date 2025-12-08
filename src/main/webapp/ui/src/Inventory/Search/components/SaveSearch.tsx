import BookmarkBorderOutlinedIcon from "@mui/icons-material/BookmarkBorderOutlined";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import { makeStyles } from "tss-react/mui";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import NameDialog from "./NameDialog";

const useStyles = makeStyles()((theme) => ({
    button: {
        padding: theme.spacing(0.5),
        margin: theme.spacing(-0.5, 0.125, -0.5, -0.5),
    },
}));

function SaveSearch(): React.ReactNode {
    const { search } = useContext(SearchContext);
    const { searchStore } = useStores();
    const { classes } = useStyles();

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
                    className={classes.button}
                    icon={<BookmarkBorderOutlinedIcon fontSize="small" />}
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
