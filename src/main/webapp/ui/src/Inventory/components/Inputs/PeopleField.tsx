import Autocomplete, { autocompleteClasses } from "@mui/material/Autocomplete";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import type { Person, Username } from "../../../stores/definitions/Person";
import type PersonModel from "../../../stores/models/PersonModel";
import { sortPeople } from "../../../stores/models/PersonModel";
import useStores from "../../../stores/use-stores";
import RsSet, { nullishToSingleton, unionWith } from "../../../util/set";

type PeopleFieldArgs = {
  onSelection: (person: Person | null, doSearch?: boolean | null) => Promise<void> | void;
  label?: string;
  outsideGroup?: boolean;
  recipient: PersonModel | null;
  excludedUsernames?: RsSet<Username>;
  /**
   * When set, the field offers only this one person as an option (no group
   * members, no free-text search) rather than the caller's usual pool of
   * choices. For use when exactly one recipient is already known to be
   * correct and no other choice should be offered.
   */
  restrictToUser?: PersonModel;
  /**
   * Suppresses the dropdown auto-opening when the field receives focus.
   * Kept separate from `restrictToUser`: a caller that pre-populates the
   * recipient asynchronously (e.g. via an API lookup after mount) only has
   * `restrictToUser` set on a later render, by which point the field's
   * `autoFocus` has already fired against `openOnFocus`'s earlier value.
   * This flag lets such a caller declare "don't auto-open" synchronously
   * from the very first render, independent of when the restricted person
   * itself resolves.
   */
  disableAutoOpen?: boolean;
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
  restrictToUser,
  disableAutoOpen,
}: PeopleFieldArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    peopleStore,
    searchStore: { search },
  } = useStores();
  const { addAlert } = useContext(AlertContext);

  const [searchResults, setSearchResults] = useState<RsSet<PersonModel>>(new RsSet<PersonModel>());

  const handleUserChange = (user: Person | null, doSearch?: boolean) => {
    void onSelection(user, doSearch);
  };

  const ownedBy = search.fetcher.ownedBy;
  const owner = search.fetcher.owner;

  useEffect(() => {
    let isMounted = true;
    /* 1 - fetch and store group members */
    if (!restrictToUser && peopleStore.currentUser) {
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
                title: t("peopleField.errors.couldNotGetGroupMembers"),
                message: e.message,
                variant: "error",
              }),
            );
          }
        });
    }
    return () => {
      isMounted = false;
    };
  }, [peopleStore.currentUser]);

  const searchPeople = (searchTerm: string) => {
    if (outsideGroup && !restrictToUser) {
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

  const loading = !restrictToUser && peopleStore.groupMembers === null;
  const allUsers = restrictToUser
    ? [restrictToUser]
    : sortPeople(
        [
          ...unionWith<PersonModel, string>(
            (x: PersonModel) => x.username,
            [
              peopleStore.groupMembers ?? new RsSet<PersonModel>(),
              searchResults,
              nullishToSingleton(peopleStore.currentUser),
            ],
          ).filter((u: PersonModel) => !(excludedUsernames ?? new RsSet<Username>()).has(u.username)),
        ],
        { placeCurrentFirst: true },
      );

  return (
    <Autocomplete<PersonModel>
      sx={{
        maxWidth: 500,
        [`& .${autocompleteClasses.option}`]: { cursor: "default" },
      }}
      options={allUsers}
      groupBy={(u: PersonModel) => u.groupByLabel}
      getOptionLabel={(u: Person) => u.label}
      renderInput={({ slotProps: inputSlotProps, ...rest }) => (
        <TextField
          {...rest}
          variant="outlined"
          autoFocus
          slotProps={{
            ...inputSlotProps,
            input: {
              ...inputSlotProps?.input,
              ...(label !== undefined
                ? {
                    startAdornment: <InputAdornment position="start"> {label}</InputAdornment>,
                  }
                : {}),
              endAdornment: (
                <>
                  {loading && <CircularProgress color="inherit" size={20} />}
                  {inputSlotProps?.input.endAdornment ?? null}
                </>
              ),
            },
          }}
        />
      )}
      size="small"
      value={recipient}
      onChange={(_: React.SyntheticEvent, user: Person | null) => handleUserChange(user)}
      onInputChange={(_: React.SyntheticEvent, searchTerm: string) => searchPeople(searchTerm)}
      isOptionEqualToValue={(option: Person, value: Person) => option.username === value.username}
      // When restricted to a single, already-known-correct recipient, there's nothing useful
      // to browse, so don't pop the (single-item) dropdown open as soon as the field focuses.
      // `disableAutoOpen` is checked independently of `restrictToUser` itself, since a caller
      // may only know the restricted person asynchronously, after this first render.
      openOnFocus={!restrictToUser && !disableAutoOpen}
      loading={loading}
    />
  );
}

export default observer(PeopleField);
