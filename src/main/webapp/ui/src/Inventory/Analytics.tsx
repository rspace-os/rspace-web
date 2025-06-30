import React, { useEffect, useContext } from "react";
import AnalyticsContext from "../stores/contexts/Analytics";
import useStores from "../stores/use-stores";
import { observer, useLocalObservable } from "mobx-react-lite";
import { runInAction } from "mobx";

type AnalyticsArgs = {
  children: React.ReactNode;
};

/**
 * This component sits with the context of another AnalyticsContext
 * (../components/Analytics.js) and creates a new AnalyticsContext for all of
 * the components beneath it in the component tree. This allows us to ensure
 * that all analytics events triggered from within Inventory have the `source`
 * attribute of "Inventory" set and make the AnalyticsContext system backwards
 * compatible with the TrackingStore system which remains in use across the
 * Inventory code.
 */
function Analytics({ children }: AnalyticsArgs): React.ReactNode {
  const analyticsContext = useContext(AnalyticsContext);
  const { trackingStore } = useStores();

  /**
   * We take a copy of the isAvailable and trackEvent variables, so that
   * components further down the component tree can reliably observe any
   * changes to these values.
   *   - isAvailable is simply a copy of the parent context; once the fact that
   *     an analytics engine -- be it segment or posthog -- is available has
   *     been determined we don't need to fiddle with it. It just gets
   *     propagated down the component tree.
   *   - trackEvent, on the other hand, gets adapted to attach the additional
   *     property of `source: "inventory"` to tracking call.
   */

  const value = useLocalObservable<{
    isAvailable: boolean | null;
    trackEvent: (event: string, properties?: Record<string, unknown>) => void;
  }>(() => ({
    isAvailable: null,
    trackEvent: () => {},
  }));

  const trackInventoryEvent =
    (trackEvent: (event: string, properties?: Record<string, unknown>) => void) =>
    (event: string, properties?: Record<string, unknown>) => {
      trackEvent(event, {
        ...(properties ?? {}),
        source: "inventory",
      });
    };

  useEffect(() => {
    runInAction(() => {
      const fn = trackInventoryEvent(analyticsContext.trackEvent);
      value.trackEvent = fn;
      /*
       * We also attach a reference to this new trackEvent function to the
       * trackingStore so that the older Inventory code which does not use the
       * AnalyticsContext can still trigger events.
       */
      trackingStore.trackEvent = fn;
    });
  }, [analyticsContext.trackEvent]);

  useEffect(() => {
    runInAction(() => {
      value.isAvailable = analyticsContext.isAvailable;
    });
  }, [analyticsContext.isAvailable]);

  return (
    <AnalyticsContext.Provider value={value}>
      {children}
    </AnalyticsContext.Provider>
  );
}

export default observer(Analytics);
