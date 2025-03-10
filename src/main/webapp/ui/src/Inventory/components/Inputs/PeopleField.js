//@flow

import React, {
  useEffect,
  useState,
  type Node,
  type ComponentType,
  type ElementProps,
  useContext,
} from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import RsSet, { unionWith, nullishToSingleton } from "../../../util/set";
import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import PersonModel, { sortPeople } from "../../../stores/models/PersonModel";
import { type Username, type Person } from "../../../stores/definitions/Person";
import { withStyles } from "Styles";
import { faSpinner, faHandHolding } from "@fortawesome/free-solid-svg-icons";
import { library } from "@fortawesome/fontawesome-svg-core";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
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

type PeopleFieldArgs = {|
  onSelection: (?Person, ?boolean) => Promise<void> | void,
  label: string,
  outsideGroup?: boolean,
  recipient: ?Person,
  excludedUsernames?: RsSet<Username>,
|};

/*
 * Autocomplete input field for searching for and selecting a user.
 */
function PeopleField({
  onSelection,
  label,
  outsideGroup = true,
  recipient,
  excludedUsernames,
}: PeopleFieldArgs): Node {
  const {
    peopleStore,
    searchStore: { search },
  } = useStores();
  const { addAlert } = useContext(AlertContext);

  const [searchResults, setSearchResults] = useState<RsSet<PersonModel>>(
    new RsSet()
  );

  const handleUserChange = (user: ?Person, doSearch?: boolean) => {
    void onSelection(user, doSearch);
  };

  const ownedBy = search.fetcher.ownedBy;
  const owner = search.fetcher.owner;

  useEffect(() => {
    let isMounted = true;
    /* 1 - fetch and store group members */
    if (peopleStore.currentUser) {
      void peopleStore
        .fetchMembersOfSameGroup()
        .then((members) => {
          /* 2 - set owner if defined in URL and not in store yet (direct access / bookmark case) */
          const shouldSetOwner = members && ownedBy && !owner;
          if (isMounted && shouldSetOwner) {
            const ownerToSet = [...members].find((p) => p.username === ownedBy);
            handleUserChange(ownerToSet, false);
          }
        })
        .catch((e) => {
          addAlert(
            mkAlert({
              title: "Could not get members of your groups",
              message: e.message,
              variant: "error",
            })
          );
        });
    }
    return () => {
      isMounted = false;
    };
  }, [peopleStore.currentUser]);

  const searchPeople = (searchTerm: string) => {
    if (outsideGroup) {
      peopleStore
        .searchPeople(searchTerm)
        .then((people) => {
          setSearchResults(people);
        })
        .catch(() => {
          setSearchResults(new RsSet());
        });
    }
  };

  const loading = peopleStore.groupMembers === null;
  const allUsers = sortPeople(
    [
      ...unionWith(
        (x) => x.username,
        [
          peopleStore.groupMembers ?? new RsSet(),
          searchResults,
          nullishToSingleton(peopleStore.currentUser),
        ]
      ).filter((u) => !(excludedUsernames ?? new RsSet()).has(u.username)),
    ],
    { placeCurrentFirst: true }
  );

  return (
    <CustomAutocomplete
      loading={loading}
      options={allUsers}
      groupBy={(u) => u.groupByLabel}
      getOptionLabel={(u) => u.label}
      renderInput={(props) => (
        <RecipientTextField {...props} loading={loading} label={label} />
      )}
      size="small"
      value={recipient}
      onChange={(_, user) => handleUserChange(user)}
      onInputChange={(_, searchTerm) => searchPeople(searchTerm)}
      isOptionEqualToValue={(option, value) =>
        option.username === value.username
      }
      openOnFocus
    />
  );
}

export default (observer(PeopleField): ComponentType<PeopleFieldArgs>);
