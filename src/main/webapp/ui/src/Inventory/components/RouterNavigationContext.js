//@flow
import React, { type Node } from "react";
import NavigateContext from "../../stores/contexts/Navigate";
import * as ReactRouter from "react-router";
import { useLocation } from "react-router-dom";
import useStores from "../../stores/use-stores";

type RouterNavigationContextArgs = {|
  children: Node,
|};

/*
 * This NavigationContext encompasses all of the pages under this React app.
 * This includes all of Inventory and some other pages like standlone List of
 * Materials. All navigations within the app are forward to react-router and
 * any navigations to outside are opened in a new window.
 */
export default function RouterNavigationContext({
  children,
}: RouterNavigationContextArgs): Node {
  const navigate = ReactRouter.useNavigate();
  const { uiStore } = useStores();

  const useNavigate =
    () =>
    (
      url: string,
      opts: ?{| skipToParentContext?: boolean, modifyVisiblePanel?: boolean |}
    ) => {
      const { modifyVisiblePanel = true } = opts ?? {
        skipToParentContext: false,
        modifyVisiblePanel: true,
      };
      if (/\/inventory/.test(url)) {
        navigate(url);
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
