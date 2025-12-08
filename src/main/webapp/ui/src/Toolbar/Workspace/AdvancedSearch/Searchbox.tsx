import Input from "@mui/material/Input";
import type React from "react";
import SearchDialog from "../../../components/SearchDialog";
import useIsTextWiderThanField from "../../../hooks/ui/useIsTextWiderThanField";

type SearchboxArgs = {
    idx: number;
    query: {
        error: string | null;
        term: string;
    };
    onChange: (event: { target: { value: string } }) => void;
    onSubmit: () => void;
};

export default function Searchbox({ idx, query, onChange, onSubmit }: SearchboxArgs): React.ReactNode {
    const { inputRef, textTooWide } = useIsTextWiderThanField();
    return (
        <Input
            inputRef={inputRef}
            data-test-id={`a-search-input-${idx}`}
            error={query.error !== null && typeof query.error !== "undefined"}
            placeholder={query.error || "Search term"}
            inputProps={{ "aria-label": "Search term" }}
            value={query.term}
            onChange={onChange}
            endAdornment={
                <SearchDialog
                    visible={textTooWide.orElse(false)}
                    onSubmit={onSubmit}
                    query={query.term}
                    setQuery={onChange}
                />
            }
        />
    );
}
