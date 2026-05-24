import React, { useState } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import RsSet from "../../../util/set";
import Autocomplete, {
  type AutocompleteRenderInputParams,
} from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import { type Group } from "../../../stores/definitions/Group";
const RecipientTextField = ({
  slotProps,
  loading,
  label,
  ...rest
}: {
  slotProps?: AutocompleteRenderInputParams["slotProps"];
  loading: boolean;
  label: string;
} & Omit<React.ComponentProps<typeof TextField>, "slotProps">) => (
  <TextField
    {...rest}
    variant="outlined"
    autoFocus
    slotProps={{
      ...slotProps,
      input: {
        ...slotProps?.input,
        startAdornment: (
          <InputAdornment position="start">&nbsp;{label}</InputAdornment>
        ),
        endAdornment: (
          <>
            {loading && <CircularProgress color="inherit" size={20} />}
            {slotProps?.input.endAdornment ?? null}
          </>
        ),
      },
    }}
  />
);

type GroupsFieldArgs = {
  onSelection: (selectedGroup: Group) => Promise<void> | void;
  label: string;
  getOptionDisabled: (group: Group) => boolean;
};

/*
 * Autocomplete input field for searching for and selecting a group.
 */
function GroupsField({
  onSelection,
  label,
  getOptionDisabled,
}: GroupsFieldArgs): React.ReactNode {
  const { peopleStore } = useStores();

  const [searchResults, setSearchResults] = useState<Set<Group>>(new RsSet());

  const handleGroupChange = (group: Group) => {
    void onSelection(group);
  };

  const searchGroups = (searchTerm: string) => {
    peopleStore
      .searchGroups(searchTerm)
      .then((people) => {
        setSearchResults(people);
      })
      .catch(() => {
        setSearchResults(new RsSet());
      });
  };

  return (
    <Autocomplete<Group>
      sx={{ maxWidth: 500, "& .MuiAutocomplete-option": { cursor: "default" } }}
      loading={false}
      options={[...searchResults]}
      getOptionLabel={(g) => g.name}
      getOptionDisabled={getOptionDisabled}
      renderInput={(props) => (
        <RecipientTextField {...props} loading={false} label={label} />
      )}
      size="small"
      value={null}
      onChange={(_, group) => {
        handleGroupChange(group as Group);
      }}
      onInputChange={(_, searchTerm) => {
        searchGroups(searchTerm);
      }}
      openOnFocus
    />
  );
}

export default observer(GroupsField);
