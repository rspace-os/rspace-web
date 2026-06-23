import Autocomplete, { autocompleteClasses } from "@mui/material/Autocomplete";
import InputAdornment from "@mui/material/InputAdornment";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import type { Group } from "../../../stores/definitions/Group";
import useStores from "../../../stores/use-stores";

type GroupsFieldArgs = {
  onSelection: (selectedGroup: Group) => Promise<void> | void;
  label: string;
  getOptionDisabled: (group: Group) => boolean;
};

/*
 * Autocomplete input field for searching for and selecting a group.
 */
function GroupsField({ onSelection, label, getOptionDisabled }: GroupsFieldArgs): React.ReactNode {
  const { peopleStore } = useStores();

  const [searchResults, setSearchResults] = useState<Set<Group>>(new Set());

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
        setSearchResults(new Set());
      });
  };

  return (
    <Autocomplete<Group>
      sx={{
        maxWidth: 500,
        [`& .${autocompleteClasses.option}`]: { cursor: "default" },
      }}
      loading={false}
      options={[...searchResults]}
      getOptionLabel={(g) => g.name}
      getOptionDisabled={getOptionDisabled}
      renderInput={({ slotProps: inputSlotProps, ...rest }) => (
        <TextField
          {...rest}
          variant="outlined"
          autoFocus
          slotProps={{
            ...inputSlotProps,
            input: {
              ...inputSlotProps?.input,
              startAdornment: <InputAdornment position="start">&nbsp;{label}</InputAdornment>,
              endAdornment: inputSlotProps?.input.endAdornment ?? null,
            },
          }}
        />
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
