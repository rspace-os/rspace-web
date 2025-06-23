import React, { useState, useContext } from "react";
import useStores from "../../../stores/use-stores";
import SearchContext from "../../../stores/contexts/Search";
import { observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";
import TypeFilter from "./TypeFilter";
import StatusFilter from "./StatusFilter";
import Popover from "@mui/material/Popover";
import PeopleField from "../../components/Inputs/PeopleField";
import DropdownButton from "../../../components/DropdownButton";
import SavedList from "./SavedList";
import NavigateContext from "../../../stores/contexts/Navigate";
import { type SavedSearch } from "../../../stores/stores/SearchStore";
import {
  dropProperty,
  isInventoryPermalink,
  doNotAwait,
} from "../../../util/Util";
import BarcodeScanner from "../../components/BarcodeScanner/BarcodeScanner";
import BasketModel from "../../../stores/models/Basket";
import TagsCombobox from "../../../components/Tags/TagsCombobox";
import RsSet from "../../../util/set";
import { type Tag } from "../../../stores/definitions/Tag";

type PanelProps = {
  anchorEl: HTMLElement | null;
  children: React.ReactNode;
  onClose: () => void;
};

const Panel = ({ anchorEl, children, onClose }: PanelProps) => (
  <Popover
    open={Boolean(anchorEl)}
    anchorEl={anchorEl}
    onClose={onClose}
    anchorOrigin={{
      vertical: "bottom",
      horizontal: "center",
    }}
    transformOrigin={{
      vertical: "top",
      horizontal: "center",
    }}
    PaperProps={{
      variant: "outlined",
      elevation: 0,
      style: {
        minWidth: 300,
      },
    }}
  >
    {Boolean(anchorEl) && children}
  </Popover>
);

export type SavedItem = SavedSearch | BasketModel;

function SearchParameterControls(): React.ReactNode {
  const { searchStore } = useStores();
  const { search } = useContext(SearchContext);
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const [typeDropdown, setTypeDropdown] = useState<HTMLElement | null>(null);
  const [statusDropdown, setStatusDropdown] = useState<HTMLElement | null>(
    null
  );
  const [ownerDropdown, setOwnerDropdown] = useState<HTMLElement | null>(null);
  const [benchDropdown, setBenchDropdown] = useState<HTMLElement | null>(null);
  const [scanDropdown, setScanDropdown] = useState<HTMLElement | null>(null);
  const [savedSearchesDropdown, setSavedSearchesDropdown] =
    useState<HTMLElement | null>(null);
  const [savedBasketsDropdown, setSavedBasketsDropdown] =
    useState<HTMLElement | null>(null);
  const [tagsDropdown, setTagsDropdown] = useState<HTMLElement | null>(null);

  return (
    <Grid container direction="row" spacing={1}>
      <DropdownButton
        name="Type"
        onClick={({ target }) => {
          setTypeDropdown(target as HTMLElement);
        }}
        disabled={!search.showTypeFilter}
      >
        <TypeFilter
          anchorEl={typeDropdown}
          current={search.fetcher.resultType ?? "ALL"}
          onClose={(resultType) => {
            setTypeDropdown(null);
            if (search.fetcher.resultType !== resultType)
              search.setTypeFilter(resultType);
          }}
        />
      </DropdownButton>
      <DropdownButton
        name="Owner"
        onClick={({ target }) => {
          setOwnerDropdown(target as HTMLElement);
        }}
        disabled={!search.showOwnershipFilter}
      >
        <Panel anchorEl={ownerDropdown} onClose={() => setOwnerDropdown(null)}>
          <PeopleField
            onSelection={(user, doSearch) => {
              setOwnerDropdown(null);
              search.setOwner(user, doSearch as boolean | undefined);
            }}
            recipient={search.fetcher.owner as any}
            outsideGroup={false}
            label=""
          />
        </Panel>
      </DropdownButton>
      <DropdownButton
        name="Bench"
        onClick={({ target }) => {
          setBenchDropdown(target as HTMLElement);
        }}
        disabled={!search.showBenchFilter}
      >
        <Panel anchorEl={benchDropdown} onClose={() => setBenchDropdown(null)}>
          <PeopleField
            onSelection={(user, doSearch) => {
              setBenchDropdown(null);
              if (
                ["SAMPLE", "TEMPLATE"].includes(
                  search.fetcher.resultType as string
                )
              ) {
                search.setTypeFilter("ALL");
              }
              search.setBench(user, doSearch as boolean | undefined);
            }}
            recipient={search.fetcher.benchOwner as any}
            outsideGroup={false}
            label=""
          />
        </Panel>
      </DropdownButton>
      <DropdownButton
        name="Status"
        onClick={({ target }) => {
          setStatusDropdown(target as HTMLElement);
        }}
        disabled={!search.showStatusFilter}
      >
        <StatusFilter
          anchorEl={statusDropdown}
          current={search.fetcher.deletedItems}
          onClose={(status) => {
            setStatusDropdown(null);
            if (search.fetcher.deletedItems !== status)
              search.setDeletedItems(status);
          }}
        />
      </DropdownButton>
      <DropdownButton
        name="Barcode"
        disabled={!search.showBarcodeScan}
        onClick={({ target }) => {
          setScanDropdown(target as HTMLElement);
        }}
      >
        <Panel anchorEl={scanDropdown} onClose={() => setScanDropdown(null)}>
          <BarcodeScanner
            onClose={() => setScanDropdown(null)}
            onScan={(barcode) => {
              if (isInventoryPermalink(barcode.rawValue)) {
                window.location.href = barcode.rawValue;
              } else {
                const params = search.fetcher.generateNewQuery({
                  query: barcode.rawValue,
                });
                navigate(`/inventory/search?${params.toString()}`);
              }
            }}
            buttonPrefix="Search"
          />
        </Panel>
      </DropdownButton>
      <DropdownButton
        name="Tags"
        onClick={(e) => {
          /*
           * For some reason, after the TagsCombobox popup has opened this
           * button remains focussed. When the user taps the Enter key after
           * typing in a new tag, they instantly trigger the opening the dialog
           * after it closes, and thus the popup does not close. Therefore, we
           * manually blur the focus to ensure the popup closes correctly.
           */
          (e.target as HTMLElement).blur();

          setTagsDropdown(e.target as HTMLElement);
        }}
        disabled={!search.showTagsFilter}
      >
        <TagsCombobox
          enforceOntologies={false}
          value={new RsSet<Tag>([])}
          onSelection={(tag) => {
            navigate(`/inventory/search?query=l: (tags:"${tag.value}")`);
          }}
          onClose={() => {
            setTagsDropdown(null);
          }}
          anchorEl={tagsDropdown}
        />
      </DropdownButton>
      <DropdownButton
        name="Saved Searches"
        disabled={!search.showSavedSearches}
        onClick={({ target }) => {
          setSavedSearchesDropdown(target as HTMLElement);
        }}
      >
        <SavedList
          anchorEl={savedSearchesDropdown}
          itemType="searches"
          items={searchStore.savedSearches}
          onSelect={(savedSearch: SavedSearch | null) => {
            setSavedSearchesDropdown(null);
            if (savedSearch) {
              /*
               * The other search controls manipulate just one parameter,
               * whereas applying a saved search requires unsetting all
               * parameters and setting those that have been saved. Easiest
               * to do this by navigating, with the navigation context
               * faciliating scoped navigation e.g. with the picker.
               */
              const params = search.fetcher.generateNewQuery(
                dropProperty(savedSearch, "name")
              );
              navigate(`/inventory/search?${params.toString()}`);
            }
          }}
          isDisabled={(savedSearch: SavedSearch) =>
            typeof savedSearch.resultType !== "undefined" &&
            savedSearch.resultType !== null &&
            savedSearch.resultType !== "ALL" &&
            !search.uiConfig.allowedTypeFilters.has(savedSearch.resultType)
          }
        />
      </DropdownButton>
      <DropdownButton
        name="Baskets"
        disabled={!search.showSavedBaskets}
        onClick={doNotAwait(async ({ target }) => {
          await searchStore.getBaskets();
          setSavedBasketsDropdown(target as HTMLElement);
        })}
      >
        <SavedList
          anchorEl={savedBasketsDropdown}
          itemType="baskets"
          items={searchStore.savedBaskets}
          onSelect={(savedBasket: BasketModel | null) => {
            setSavedBasketsDropdown(null);
            if (savedBasket) {
              const params = search.fetcher.generateNewQuery({
                parentGlobalId: savedBasket.globalId,
                deletedItems: "INCLUDE",
              });
              navigate(`/inventory/search?${params.toString()}`);
            }
          }}
        />
      </DropdownButton>
    </Grid>
  );
}

export default observer(SearchParameterControls);
