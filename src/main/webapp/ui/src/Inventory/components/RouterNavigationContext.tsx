// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React, { type ReactNode } from "react";
import { useLocation, useNavigate as useReactRouterNavigate } from "react-router-dom";
import NavigateContext from "../../stores/contexts/Navigate";
import useStores from "../../stores/use-stores";
// biome-ignore lint/style/useImportType: initial biome migration
import { type URL } from "../../util/types";

type RouterNavigationContextArgs = {
  children: ReactNode;
};

/*
 * This NavigationContext encompasses all of the pages under this React app.
 * This includes all of Inventory and some other pages like standlone List of
 * Materials. All navigations within the app are forward to react-router and
 * any navigations to outside are opened in a new window.
 */
export default function RouterNavigationContext({ children }: RouterNavigationContextArgs): ReactNode {
  const navigate = useReactRouterNavigate();
  const { uiStore } = useStores();

  const useNavigate = () => (url: URL, opts?: { skipToParentContext?: boolean; modifyVisiblePanel?: boolean }) => {
    const { modifyVisiblePanel = true } = opts ?? {
      skipToParentContext: false,
      modifyVisiblePanel: true,
    };
    if (/\/inventory/.test(url)) {
      void navigate(url);
      if (modifyVisiblePanel) uiStore.setVisiblePanel("left");
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
