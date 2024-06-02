//@flow

import React, {
  useState,
  type Node,
  type ComponentType,
  type ElementProps,
} from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import RsSet from "../../../util/set";
import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import { type Group } from "../../../stores/definitions/Group";
import { withStyles } from "Styles";
import { faSpinner, faHandHolding } from "@fortawesome/free-solid-svg-icons";
import { library } from "@fortawesome/fontawesome-svg-core";
library.add(faSpinner, faHandHolding);

const CustomAutocomplete = withStyles<
  ElementProps<typeof Autocomplete>,
  { root: string, option: string }
>(() => ({
  root: {
    maxWidth: 500,
  },
  option: {
    cursor: "default",
  },
}))(Autocomplete);

const RecipientTextField = ({
  InputProps,
  loading,
  label,
  ...rest
}: {
  InputProps: { endAdornment: Node },
  loading: boolean,
  label: string,
}) => (
  <TextField
    {...rest}
    variant="outlined"
    autoFocus
    InputProps={{
      ...InputProps,
      startAdornment: (
        <InputAdornment position="start">&nbsp;{label}</InputAdornment>
      ),
      endAdornment: (
        <>
          {loading && <CircularProgress color="inherit" size={20} />}
          {InputProps.endAdornment}
        </>
      ),
    }}
  />
);

type GroupsFieldArgs = {|
  onSelection: (Group) => Promise<void> | void,
  label: string,
  getOptionDisabled: (Group) => boolean,
|};

/*
 * Autocomplete input field for searching for and selecting a group.
 */
function GroupsField({
  onSelection,
  label,
  getOptionDisabled,
}: GroupsFieldArgs): Node {
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
    <CustomAutocomplete
      loading={false}
      options={[...searchResults]}
      groupBy={(u) => u.groupByLabel}
      getOptionLabel={(u) => u.name}
      getOptionDisabled={getOptionDisabled}
      renderInput={(props) => (
        <RecipientTextField {...props} loading={false} label={label} />
      )}
      size="small"
      value={null}
      onChange={(_, group) => {
        handleGroupChange(group);
      }}
      onInputChange={(_, searchTerm) => {
        searchGroups(searchTerm);
      }}
      getOptionSelected={(a, b) => a.id === b.id}
      openOnFocus
    />
  );
}

export default (observer(GroupsField): ComponentType<GroupsFieldArgs>);
