//@flow

import React, { type Context, type Node } from "react";
import { useSearchParams } from "react-router-dom";

/*
 * There are various parts of the UI where a standalone page, like List of
 * Materials or Gallery, is embedded on another page in a dialog. These user
 * interfaces often have state that when that UI is rendered on its own page
 * should be synchronised with the browser's location search params so that
 * back, forward, and bookmarking work but when rendered as an embed on another
 * page should instead by a regular piece of state managed by React.useState.
 * This module provides various pieces of functionality to allow for components
 * to be agnostic as to which is being used.
 */

type Mode = "UseState" | "SearchParam";

const SearchParamStateContext: Context<Mode> =
  React.createContext("SearchParam");

export function CaptureSearchParamsAsUseState({
  children,
}: {|
  children: Node,
|}): Node {
  return (
    <SearchParamStateContext.Provider value={"UseState"}>
      {children}
    </SearchParamStateContext.Provider>
  );
}

export function useSearchParamState<
  T:
    | Array<[string, string]>
    | { [key: string]: string | Array<string>, ... }
    | URLSearchParams
>(initialValue: T): [T, (T) => void] {
  const mode = React.useContext(SearchParamStateContext);
  const [, setSearchParams] = useSearchParams();
  const [state, setState] = React.useState(initialValue);
  return [
    state,
    (newValue) => {
      setState(newValue);
      if (mode === "SearchParam") setSearchParams(newValue);
    },
  ];
}
