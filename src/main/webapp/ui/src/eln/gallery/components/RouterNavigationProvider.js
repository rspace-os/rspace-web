//@flow
import React, { type Node } from "react";
import NavigateContext from "../../../stores/contexts/Navigate";
import * as ReactRouter from "react-router";
import { useLocation } from "react-router-dom";

type RouterNavigationProviderArgs = {|
  children: Node,
|};

/**
 * This NavigationContext encompasses all of the pages under this React app.
 * All navigations within the app are forward to react-router and any
 * navigations to outside are opened in a new window.
 */
export default function RouterNavigationProvider({
  children,
}: RouterNavigationProviderArgs): Node {
  const navigate = ReactRouter.useNavigate();

  const useNavigate = () => (url: string) => {
    if (/\/gallery/.test(url)) {
      navigate(url);
    } else {
      window.open(location.origin + url);
    }
  };

  return (
    <NavigateContext.Provider
      value={{
        useNavigate,
        useLocation,
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}
