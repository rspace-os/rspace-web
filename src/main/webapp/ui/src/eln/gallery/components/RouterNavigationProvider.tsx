// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import { useLocation, useNavigate as useReactRouterNavigate } from "react-router-dom";
import NavigateContext from "../../../stores/contexts/Navigate";

type RouterNavigationProviderArgs = {
  children: React.ReactNode;
};

/**
 * This NavigationContext encompasses all of the pages under this React app.
 * All navigations within the app are forward to react-router and any
 * navigations to outside are opened in a new window.
 */
export default function RouterNavigationProvider({ children }: RouterNavigationProviderArgs): React.ReactNode {
  const navigate = useReactRouterNavigate();

  const useNavigate = () => (url: string) => {
    if (/\/gallery/.test(url)) {
      void navigate(url);
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
