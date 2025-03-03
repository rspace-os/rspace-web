//@flow

import React, { type Node, useContext, useEffect } from "react";
import Alerts from "../../components/Alerts/Alerts";
import AlertContext from "../../stores/contexts/Alert";
import useStores from "../../stores/use-stores";

/*
 * An inner component is necessary because the Outer component defines an
 * instance of the Alert react context via its call to the Alert component. To
 * bind the addAlert and removeAlert functions to UiStore, we need to be
 * within the part of the component tree that is inside the scope of Alert
 * context.
 */
const Inner = () => {
  const { addAlert, removeAlert } = useContext(AlertContext);
  const { uiStore } = useStores();

  useEffect(() => {
    uiStore.addAlert = addAlert;
    uiStore.removeAlert = removeAlert;
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - addAlert will not change
     */
  }, [uiStore]);

  return <> </>;
};

type OuterArgs = {|
  children: Node,
|};

/**
 * This component is an adapter between the Alert context-based approach to
 * showing alerts, as used by the Alerts component, and the original way to
 * display alerts in Inventory using UiStore. This adapter means that we don't
 * have re-write all of the code that displays alerts in Inventory to use the
 * context-based approach, whilst still being able to use the context-based
 * approach under the hood so that we only need one way to display alerts in
 * the codebase. Reworking Inventory to use the context-based approach would
 * be particularly difficult because there are various places in the code that
 * rely on the fact that UiStore is accessible as a global variable because
 * they are not inside the react runtime.
 *
 * This file is called Alert, even though the component Outer is exported,
 * because including it in the Inventory pages has the same effect as
 * including the general Alert component (../../components/Alerts/Alerts) and
 * should be used in the same way.
 */
export default function Outer({ children }: OuterArgs): Node {
  return (
    <Alerts>
      <Inner />
      {children}
    </Alerts>
  );
}
