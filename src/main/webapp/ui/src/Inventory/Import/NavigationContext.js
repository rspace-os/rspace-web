//@flow

import React, { type Node, useContext } from "react";
import NavigateContext from "../../stores/contexts/Navigate";
import useStores from "../../stores/use-stores";

type NavigationContextArgs = {|
  children: Node,
|};

/*
 * This a component that provides a wrapper around the entire Upload UI,
 * encasing it in a NavigateContext, with custom navigation logic. Any
 * NavigateContext that wraps this component is used to perform the navigation,
 * thereby composing the logic.
 */
export default function NavigationContext({
  children,
}: NavigationContextArgs): Node {
  const { useNavigate, useLocation } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { uiStore } = useStores();
  const newUseNavigate = () => (url: string) => {
    if (/import/.test(url)) {
      navigate(url);
    } else {
      /*
       * If the user is navigating away from the Import UI (i.e. the
       * destination URL does not include the string "import") then we want to
       * check with the user if it is ok to discard their changes. Otherwise,
       * there is no such warning until some other piece of code goes to use
       * the UiStore's capability to track changes and the import system loses
       * the ability to its track changes; best to simply discard upon leaving.
       */
      void uiStore.confirmDiscardAnyChanges().then((discard) => {
        if (discard) navigate(url);
      });
    }
  };

  return (
    <NavigateContext.Provider
      value={{
        useNavigate: newUseNavigate,
        useLocation,
      }}
    >
      {children}
    </NavigateContext.Provider>
  );
}
