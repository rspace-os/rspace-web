import React, { useEffect, useState, useContext, type ReactNode } from "react";
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
  React.ComponentProps<typeof Autocomplete<PersonModel>>,
  { root: string; option: string }
>(() => ({
  root: {
    maxWidth: 500,
  },
  option: {
    cursor: "default",
  },
}))(Autocomplete<PersonModel>);

type RecipientTextFieldArgs = {
  InputProps: { endAdornment: React.ReactNode };
  loading: boolean;
  label: string;
} & React.ComponentProps<typeof TextField>;

const RecipientTextField = ({
  InputProps,
  loading,
  label,
  ...rest
}: RecipientTextFieldArgs) => (
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

type PeopleFieldArgs = {
  onSelection: (
    person: Person | null,
    doSearch?: boolean | null
  ) => Promise<void> | void;
  label: string;
  outsideGroup?: boolean;
  recipient: PersonModel | null;
  excludedUsernames?: RsSet<Username>;
};

/*
 * Autocomplete input field for searching for and selecting a user.
 */
function PeopleField({
  onSelection,
  label,
  outsideGroup = true,
  recipient,
  excludedUsernames,
}: PeopleFieldArgs): ReactNode {
  const {
    peopleStore,
    searchStore: { search },
  } = useStores();
  const { addAlert } = useContext(AlertContext);

  const [searchResults, setSearchResults] = useState<RsSet<PersonModel>>(
    new RsSet<PersonModel>()
  );

  const handleUserChange = (user: Person | null, doSearch?: boolean) => {
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
            handleUserChange(ownerToSet || null, false);
          }
        })
        .catch((e) => {
          if (e instanceof Error) {
            addAlert(
              mkAlert({
                title: "Could not get members of your groups",
                message: e.message,
                variant: "error",
              })
            );
          }
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
      ...unionWith<PersonModel, string>(
        (x: PersonModel) => x.username,
        [
          peopleStore.groupMembers ?? new RsSet<PersonModel>(),
          searchResults,
          nullishToSingleton(peopleStore.currentUser),
        ]
      ).filter(
        (u: PersonModel) =>
          !(excludedUsernames ?? new RsSet<Username>()).has(u.username)
      ),
    ],
    { placeCurrentFirst: true }
  );

  return (
    <CustomAutocomplete
      options={allUsers}
      groupBy={(u: PersonModel) => u.groupByLabel}
      getOptionLabel={(u: Person) => u.label}
      renderInput={(props) => (
        <RecipientTextField {...props} loading={loading} label={label} />
      )}
      size="small"
      value={recipient}
      onChange={(_: React.SyntheticEvent, user: Person | null) =>
        handleUserChange(user)
      }
      onInputChange={(_: React.SyntheticEvent, searchTerm: string) =>
        searchPeople(searchTerm)
      }
      isOptionEqualToValue={(option: Person, value: Person) =>
        option.username === value.username
      }
      openOnFocus
      loading={loading}
    />
  );
}

export default observer(PeopleField);
