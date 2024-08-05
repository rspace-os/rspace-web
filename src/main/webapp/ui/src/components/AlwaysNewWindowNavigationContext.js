//@flow
import React, { type Node } from "react";
import NavigateContext from "../stores/contexts/Navigate";

type AlwaysNewWindowNavigationContextArgs = {|
  children: Node,
|};

/**
 * Any calls to `navigate` inside the children of this component will result in
 * the passed URL being opening in a new window. This effectively converts the
 * UI code intended for a Single Page Application into a regular web page where
 * all links have target="\_blank". This is useful for reusing components on
 * pages, like the IGSN Public Page, where the user should not be navigated
 * away.
 */
export default function AlwaysNewWindowNavigationContext({
  children,
}: AlwaysNewWindowNavigationContextArgs): Node {
  const useNavigate =
    () =>
    (
      url: string,
      _opts?: {| skipToParentContext?: boolean, modifyVisiblePanel?: boolean |}
    ) => {
      /**
       * This navigation context is designed to be a root context and thus
       * ignores the skipToParentContext argument
       */
      window.open(location.origin + url);
    };

  return (
    <NavigateContext.Provider
      value={{
        useNavigate,
        useLocation: () => ({
          hash: "",
          pathname: "",
          search: "",
          state: {},
          key: "",
        }),
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}
