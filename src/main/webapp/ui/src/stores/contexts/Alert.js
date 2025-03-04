//@flow strict

import { type Context, type Node, createContext } from "react";
import { take } from "../../util/iterators";
import { type LinkableRecord } from "../definitions/LinkableRecord";

type AlertVariant = "success" | "warning" | "error" | "notice";

/*
 * Global variable is necessary because we have to be sure that all alerts get
 * a unique id. Initially we tried using the time of creation in milliseconds
 * but there are times when two alerts get created in the same millisecond.
 * Being a primitive value and not being exported, not other code can depend
 * on this value so this isn't a huge concern, although do watch out when
 * writing jest tests.
 */
let id = 0;

/**
 * Each alert can have a number of details associated with it, which the user
 * can view by expanding the alert. The purpose of these details is to provide
 * more information about which elements being operated on succeeded, which
 * failed and why. This can form a nice workflow when coupled with the retry
 * button: some elements fail and are listed in the details, the user can go to
 * each linked record in turn to correct the issue, then retry the action.
 */
export type AlertDetails = {|
  title: string,
  variant: AlertVariant,
  record?: LinkableRecord,
  help?: string,
|};

type AlertParams = {|
  title?: ?string,
  message: string,
  variant?: AlertVariant,
  duration?: number,
  isInfinite?: boolean,
  actionLabel?: string,
  onActionClick?: () => void,
  details?: $ReadOnlyArray<AlertDetails>,
  retryFunction?: () => Promise<void>,
  allowClosing?: boolean,
  icon?: Node,
|};

/**
 * An alert that can be displayed to the user. Alerts are displayed in the top
 * right corner of the viewport. They can be dismissed by the user or be
 * automatically dismissed after a certain amount of time. They can also have
 * an action button, a retry button, a number of details, and an icon.
 */
export type Alert = {|
  title: ?string,
  message: string,
  id: number,
  variant: AlertVariant,
  duration: number,
  isInfinite: boolean,
  isOpen: boolean,
  actionLabel: ?string,
  onActionClick: () => void,
  details: Array<AlertDetails>,
  detailsCount: number,
  retryFunction: ?() => Promise<void>,
  allowClosing: boolean,
  icon: ?Node,
|};

/**
 * Smart constructor function for alerts. Various defaults are set here, such
 * as the variant, duration, and whether the alert is infinite. These can be
 * overridden by the caller. The id is set automatically and is unique across
 * all alerts.
 */
export function mkAlert(config: AlertParams): Alert {
  return {
    title: config.title,
    message: config.message,
    variant: config.variant ?? "notice",

    /*
     * Generally speaking, error messages should not auto dismiss. This is so
     * that it is not possible for a user to not notice them if they step away
     * from their computer whilst an action is being processed. Error messages
     * also typically include links to different parts of the UI and retry
     * buttons so keeping them around aids with resolving the issue. When
     * isInfinite is set, it overrides the value of the duration.
     */
    isInfinite: config.isInfinite ?? config.variant === "error",
    duration: config.duration ?? 4000,

    actionLabel: config.actionLabel ?? null,
    onActionClick: config.onActionClick ?? (() => {}),
    id: id++,
    isOpen: true,
    details: [...take(config.details ?? [], 25)],
    detailsCount: config.details?.length ?? 0,
    retryFunction: config.retryFunction,
    allowClosing: config.allowClosing ?? true,
    icon: config.icon,
  };
}

/*
 * This context allows any page to display alerts in the top right corner of
 * the viewport, be they indications that an action was successful, error
 * messages, or anything other timely information that the user should be
 * alerted of.
 *
 * To use, wrap the page in the Alert component
 * (../../components/Alerts/Alert.js) which creates an instance of this
 * context. Any components rendered anywhere in the component tree beneath the
 * component can simply do the following, importing `AlertContext` and
 * `mkAlert` from this file:
 * ```
 * const { addAlert } = useContext(AlertContext);
 * addAlert(mkAlert({
 *   message: "Action failed",
 *   variant: "error"
 * }));
 * ```
 */

type AlertContextType = {|
  /*
   * Displays an alert. Will be displayed until either its timeout expires or
   * it is passed to `removeAlert`.
   */
  addAlert: (Alert) => void,
  /*
   * Closes the alert. Passing an alert that has already been removed MUST NOT
   * error; this function SHOULD simply do nothing.
   *
   * It generally shouldn't be necessary to use this function most of the time
   * as most alerts will either timeout automatically if a timeout has been set
   * or should wait for the user to manually close them. Typical use is for
   * clearing all of the alerts as the user navigates to a different part of the
   * application where those alerts will no longer be relevant or for the
   * implementation details of displaying the alerts.
   */
  removeAlert: (Alert) => void,
|};

const DEFAULT_ALERT_CONTEXT: AlertContextType = {
  addAlert: () => {},
  removeAlert: () => {},
};

const AlertContext: Context<AlertContextType> = createContext(
  DEFAULT_ALERT_CONTEXT
);

/**
 * This context allows any part of the page to display alerts in the top right
 * corner of the viewport, be they indications that an action was successful,
 * error messages, or anything other timely information that the user should be
 * alerted of.
 *
 * Whilst the context can be used directly to add and remove alerts, it is
 * recommended that the `Alerts` component[1] be used instead, as it provides
 * an accessible way to display alerts and ensures that the alerts are
 * displayed in the correct location. The exception is test code where it may
 * be advantageous to mock the add and remove functions, rather than rendering
 * the alerts and asserting the UI.
 *
 * [1]: ../../components/Alerts/Alerts.js
 */
export default AlertContext;
