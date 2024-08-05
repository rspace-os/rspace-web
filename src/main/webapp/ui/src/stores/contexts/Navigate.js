//@flow strict

import React, { type Context } from "react";
import { type URL } from "../../util/types";
import { type Location } from "react-router-dom";

export type UseLocation = Location;

/*
 * This context is used to facilitate different approaches to navigating the
 * user across different front-end designs.
 *
 * This context should be instantiated as close to the root of the React
 * component tree as possible, such that it encompasses all components that
 * perform navigation and should provide the following values:
 */
type NavigateContextType = {|
  /*
   *  A function that should return a function that itself, given a URL, will
   *  navigate the user, however is most appropiate for that front-end. All
   *  components that navigate the user should use this context and should not
   *  navigate in any way that is specific to a given front-end design. For
   *  example, within Inventory react-router will be exposed by this context,
   *  whilst within the ELN it is most appropriate to open most links in a new
   *  tab.
   *
   *
   *  `skipToParentContext`, when passed as true, should result in the navigation
   *  happening as defined by the parent context, with any logic specified by
   *  the current context simply ignored.
   *
   *    This is useful where the parent context always performs all navigations
   *    in a new window and thus this parameter gives the caller the option of
   *    performing the navigation in a new window.
   *
   *    Another option would be where this navigation context manages the inner
   *    search and the parent context manages the page-wide search. This
   *    parameter gives the component within the inner search the option of
   *    triggering a page-wide search.
   *
   *
   *  `modifyVisiblePanel`, when passed as true or not passed at all, will
   *  allow the navigation context's to manipulate the currently visible of a
   *  multi-panel page. If it is passed as false then the currently visible
   *  panel should not change.
   *
   *    By defaulting to true, whenever the user navigates around they are
   *    returned to the left panel, where they can see the result of their
   *    navigation on the set of search results. However, there are occassions
   *    where the search results should change but the right panel should
   *    remain or become visible as it is the main focus of the user's
   *    attention.
   */
  useNavigate: () => (
    URL,
    opts?: {| skipToParentContext?: boolean, modifyVisiblePanel?: boolean |}
  ) => void,

  /*
   *  A function that returns an object that represents the current search
   *  parameters as encoded in the URL. This is only useful within the main
   *  Inventory webapp and should be mocked elsewhere.
   */
  useLocation: () => Location,
|};

/*
 * This should never be directly used because the entire app should be wrapped
 * in an actual instance
 */
const DEFAULT_NAVIGATION_CONTEXT = {
  useNavigate: () => () => {},
  useLocation: () => ({
    hash: "",
    pathname: "",
    search: "",
    state: {},
    key: "",
  }),
};

const NavigateContext: Context<NavigateContextType> = React.createContext(
  DEFAULT_NAVIGATION_CONTEXT
);

export default NavigateContext;
