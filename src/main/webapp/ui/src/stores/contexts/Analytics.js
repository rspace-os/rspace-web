//@flow strict

import { type Context, createContext } from "react";
import { makeAutoObservable } from "mobx";

type AnalyticsContextType = {|
  /**
   * isAvailable is true only if analytics are enabled and if posthog is not
   * configured then intercom has been successfully loaded. Analytics are
   * disabled on many servers for privacy and GDPR reasons.
   *
   * Any component exposing this context MUST fetch the current status of this
   * value and expose it as an observable boolean. Until the value has been
   * fetched, it MUST be null. Together, this means that components using this
   * context can use the code `await when(() => isAvailable !== null);` to wait
   * for the status to be known before attempting to use the value to alter the
   * UI. One such example being that the contact-us form inside of Lighthouse
   * is disabled, replaced with a mailto link, when analytics are disabled.
   */
  isAvailable: ?boolean,

  /**
   * When called this function reports an event to Segment, to say that the
   * user performed a particular action.
   *
   * @arg event      The name of the event, which can be any string.
   * @arg properties An optional collection of key-value pairs of data
   *                 associated with the event
   */
  trackEvent: (event: string, properties?: { ... }) => void,
|};

const DEFAULT_ANALYTICS_CONTEXT: AnalyticsContextType = makeAutoObservable({
  isAvailable: null,
  trackEvent: () => {},
});

const AnalyticsContext: Context<AnalyticsContextType> = createContext(
  DEFAULT_ANALYTICS_CONTEXT
);

/**
 * The context that provides the ability to track events with the analytics
 * service. This allows any part of the UI to quickly fire off an analytics
 * events without having to be concerned about where those events are sent and
 * how they are collated. This allows for sections of the UI to enrich the
 * events with additional metadata before they are sent.
 */
export default AnalyticsContext;
