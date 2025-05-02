import React, { type Context } from "react";
import { type Search } from "../definitions/Search";
import { type Record } from "../definitions/Record";
import getRootStore from "../stores/RootStore";

/*
 * This context is used to encapsulte the entire scope of a search operation,
 * be that Inventory's main search, the contents of different record types, or
 * other features like the Picker or the Move dialog.
 */
type SearchContextType = {
  /*
   *  This is an instance of ../models/Search, the main class that performs the
   *  actual search operation.
   */
  search: Search;

  /*
   *  This is the parent result, in the situations where the search is scoped
   *  to the records associated with some record. This might be a container
   *  where search is scoped to its contents, a sample where search is scoped
   *  to it's subsamples, or a template where search is scoped to the samples
   *  that have been created from that template.
   */
  scopedResult?: ?Record;

  /*
   *  If true, tapping on one of the records in the search result, regardless
   *  of current view, should not modify the state of the system. Moreover,
   *  checkboxes and a context menu should not be available.
   */
  disabled?: boolean;

  /*
   *  If true, when the associated result is tapped, the user will be navigated
   *  to the permalink page for the result as opposed to simply seting the
   *  active result.
   */
  isChild?: boolean;

  /*
   *  This is the search onto which new activeResults will be set. In the case
   *  of most searches, this should be same as the main search above, but in
   *  some cases it is desirable to have calls to setActiveResult be applied to
   *  a different search instance. For example, when tapping on a result of a
   *  search of a container's content the main search's activeResult should be
   *  set instead of the container's content which isn't shown.
   */
  differentSearchForSettingActiveResult: Search;
};

const DEFAULT_SEARCH_CONTEXT = {
  search: getRootStore().searchStore.search,
  differentSearchForSettingActiveResult: getRootStore().searchStore.search,
};

const SearchContext: Context<SearchContextType> = React.createContext(
  DEFAULT_SEARCH_CONTEXT
);

export default SearchContext;
