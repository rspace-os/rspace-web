import { mapValues } from "es-toolkit";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";

/**
 * This constant ensures that we don't end up with clashing keys
 */
/*
 * The keys of the UI_JSON_SETTINGS object. The backend accepts only these names on a keyed
 * preference write (UI_JSON_SETTINGS_KEYS in UserManagerImpl), so adding a preference here means
 * adding it there too; without that, writes of the new key are refused with a 400.
 */
export const PREFERENCES: { [pref: string]: symbol } = {
  GALLERY_VIEW_MODE: Symbol.for("GALLERY_VIEW_MODE"),
  GALLERY_SORT_BY: Symbol.for("GALLERY_SORT_BY"),
  GALLERY_SORT_ORDER: Symbol.for("GALLERY_SORT_ORDER"),
  GALLERY_PICKER_INITIAL_SECTION: Symbol.for("GALLERY_PICKER_INITIAL_SECTION"),
  GALLERY_SIDEBAR_OPEN: Symbol.for("GALLERY_SIDEBAR_OPEN"),
  INVENTORY_FORM_SECTIONS_EXPANDED: Symbol.for("INVENTORY_FORM_SECTIONS_EXPANDED"),
  INVENTORY_HIDDEN_RIGHT_PANEL: Symbol.for("INVENTORY_HIDDEN_RIGHT_PANEL"),
  // Legacy: the single per-process "remember" bundle for every operation type combined into one
  // ever-growing collection, which is what let a heavy user of "Remember" eventually exceed the
  // per-key size cap for good (RSDEV-1231, Codex review, PR #1090). Read-only fallback for bundles
  // saved before the per-operation keys below existed; nothing writes it anymore.
  INVENTORY_OPERATION_PROCESS_VALUES: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES"),
  // One "remember" bundle collection per operation type, so one operation's heavy use cannot crowd
  // out another's budget under a shared cap. Select the right one for the current operation with
  // `processValuesPreferenceFor` (processNames.ts) rather than referencing these directly.
  INVENTORY_OPERATION_PROCESS_VALUES_ALIQUOT: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_ALIQUOT"),
  INVENTORY_OPERATION_PROCESS_VALUES_PASSAGE: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_PASSAGE"),
  INVENTORY_OPERATION_PROCESS_VALUES_POOL: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_POOL"),
  INVENTORY_OPERATION_PROCESS_VALUES_DERIVE: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_DERIVE"),
  INVENTORY_OPERATION_PROCESS_VALUES_CRYOPRESERVE: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_CRYOPRESERVE"),
  INVENTORY_OPERATION_PROCESS_VALUES_REVIVE: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_REVIVE"),
  INVENTORY_OPERATION_PROCESS_VALUES_DESTROY: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES_DESTROY"),
  INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
  INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
  SYSADMIN_USERS_TABLE_COLUMNS: Symbol.for("SYSADMIN_USERS_TABLE_COLUMNS"),
};

type UiPreferencesContextType = {
  uiPreferences: { [key in keyof typeof PREFERENCES]: unknown };
  setUiPreferences: React.Dispatch<React.SetStateAction<{ [key in keyof typeof PREFERENCES]: unknown } | null>>;
  /*
   * One write chain per key, so two writes of the same key from one page land in the order they
   * were made. Losing a key to an overlapping writer is no longer possible: each write sends only
   * its own key and the server merges it (code review, finding 3), which is also why the chains are
   * per key rather than one shared one - a single chain would let one stalled request hold up every
   * other preference for the rest of the session, with nothing left to gain from the ordering.
   */
  pendingWrites: React.MutableRefObject<Map<string, Promise<void>>>;
};

const DEFAULT_UI_PREFERENCES_CONTEXT: UiPreferencesContextType = {
  uiPreferences: mapValues(PREFERENCES, () => null),
  setUiPreferences: () => {},
  pendingWrites: { current: new Map() },
};

const UiPreferencesContext: React.Context<UiPreferencesContextType> =
  React.createContext(DEFAULT_UI_PREFERENCES_CONTEXT);

async function fetchPreferences(): Promise<UiPreferencesContextType["uiPreferences"] | ""> {
  const { data } = await axios.get<UiPreferencesContextType["uiPreferences"] | "">(
    "/userform/ajax/preference?preference=UI_JSON_SETTINGS",
  );
  return data;
}

/**
 * This page-wide contexts fetches the UI Preferences and makes the current
 * values available to all calls to useUiPreference in child components.
 *
 * Whilst the data is being fetched, the child nodes are not rendered and so
 * calls to useUiPreference do not need to consider ongoing network activity.
 * If the network call fails, the UI Preferences default to an empty object
 * and all calls to useUiPreference will use the passed default value.
 */
export function UiPreferences({ children }: { children: React.ReactNode }): React.ReactNode {
  const [uiPreferences, setUiPreferences] = React.useState<UiPreferencesContextType["uiPreferences"] | null>(null);
  const pendingWrites = React.useRef<Map<string, Promise<void>>>(new Map());

  React.useEffect(() => {
    void fetchPreferences()
      .then((data) => {
        if (data === "") {
          setUiPreferences(mapValues(PREFERENCES, () => null) as { [key in keyof typeof PREFERENCES]: unknown });
          return;
        }
        setUiPreferences(data);
      })
      .catch(() => {
        setUiPreferences(mapValues(PREFERENCES, () => null));
      });
  }, []);

  /*
   * If it turns out that loading this data will likely take a while,
   * then we will want to replace this null with a loading spinner.
   */
  if (!uiPreferences) return null;
  return (
    <UiPreferencesContext.Provider value={{ uiPreferences, setUiPreferences, pendingWrites }}>
      {children}
    </UiPreferencesContext.Provider>
  );
}

/**
 * Read a preference's value straight out of the shared context, without binding a component to it
 * the way `useUiPreference` does. For the rare case that needs a DIFFERENT preference's current
 * value than whatever a `useUiPreference` call in the same component happens to be bound to this
 * render - e.g. computing the bundle for an operation the user is in the middle of selecting, whose
 * key `useUiPreference` will only start reading on the NEXT render (RSDEV-1231, Codex review, PR
 * #1090). Pair with `useRawUiPreferences` for the context value to pass in.
 */
export function readUiPreference<T>(
  uiPreferences: UiPreferencesContextType["uiPreferences"],
  preference: (typeof PREFERENCES)[keyof typeof PREFERENCES],
  defaultValue: T,
): T {
  const key = Symbol.keyFor(preference);
  if (key && typeof uiPreferences[key] !== "undefined") {
    return (uiPreferences[key] as { value: T })?.value ?? defaultValue;
  }
  return defaultValue;
}

/** The raw preferences map, for a caller that needs `readUiPreference` rather than one bound key. */
export function useRawUiPreferences(): UiPreferencesContextType["uiPreferences"] {
  return React.useContext(UiPreferencesContext).uiPreferences;
}

/**
 * Use this custom hook to get the value of a UI Preference from the page-wide
 * context. The returned tuple has the same shape as a call to React.useState,
 * so that the value can be updated and persisted across page loads.
 *
 * @arg preference The UI Preference in question
 *
 * @arg opts Various options, including
 *
 *      defaultValue  If the current state of UI Preferences does not include
 *                    `preference` then `defaultValue` will be returned as the
 *                    value instead.
 */
export default function useUiPreference<T>(
  preference: (typeof PREFERENCES)[keyof typeof PREFERENCES],
  opts: {
    defaultValue: T;
  },
): [T, (newValue: T) => void] {
  const { uiPreferences, setUiPreferences, pendingWrites } = React.useContext(UiPreferencesContext);
  // Not getRootStore().uiStore: this hook also serves Gallery and Sysadmin, which mount the generic
  // Alerts component, not Inventory's adapter - the only place that wires UiStore.addAlert to
  // anything real. Elsewhere it is a silent no-op, so a save failure never reached the user
  // (Codex review, PR #1090). AlertContext's default value is itself a safe no-op, so this is never
  // undefined, unlike RootStore, which is not always bootstrapped outside Inventory.
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("common");
  const key = Symbol.keyFor(preference);
  // Derived fresh from context every render, not mirrored into a local useState: a caller that
  // passes a DIFFERENT `preference` across renders of the same component instance (e.g. a wizard
  // whose active operation changes without unmounting, RSDEV-1231) needs this render's value for
  // that key, not whatever `preference` resolved to when the component first mounted. A useState
  // initializer only runs once, so it would keep serving the first key's value forever.
  const v = readUiPreference(uiPreferences, preference, opts.defaultValue);

  return [
    v,
    // Takes a VALUE, not React's SetStateAction. It was typed as a full setter but treated
    // `newValue` as a value everywhere below, so `setPref(prev => prev + 1)` stored the function
    // object in the context and JSON.stringify'd it into the POST body, persisting undefined
    // (parallel review, FE12). Every caller passes a value; narrowing the type makes the updater
    // form a compile error rather than a silent data loss.
    (newValue: T) => {
      setUiPreferences((old: { [k in keyof typeof PREFERENCES]: unknown } | null) => {
        if (old === null) return old;
        if (!key) return old;
        return {
          ...old,
          [key]: {
            value: newValue,
            time: Date.now(),
          },
        };
      });

      if (!key) return;
      const previous = pendingWrites.current.get(key) ?? Promise.resolve();
      const write = previous.then(async () => {
        const formData = new FormData();
        formData.append("preference", "UI_JSON_SETTINGS");
        // Only this key is sent; the server merges it into the stored object in one transaction.
        // Reading the whole object here first and posting it back was the collision: two writers
        // that overlapped both merged into the same snapshot and the later one dropped the
        // other's key (code review, finding 3).
        formData.append("key", key);
        formData.append(
          "value",
          JSON.stringify({
            value: newValue,
            // we save the time so that we have the option of implementing an
            // eviction polciy in the future
            time: Date.now(),
          }),
        );
        await axios.post<unknown>("/userform/ajax/preference", formData);
      });
      // Caught so a failure cannot block this key's chain (callers never await it), but reported
      // two ways: logged for a developer, and alerted for the user. A silently dropped preference
      // save used to be invisible to both - the session kept working off the optimistic local
      // state above, so nothing looked wrong until a later login found the save had never landed
      // (RSDEV-1231, Codex review, PR #1090).
      pendingWrites.current.set(
        key,
        write.catch((e) => {
          console.error(`Could not save UI preference ${key}`, e);
          // Never let raising the alert itself throw past this point: a throw here would make
          // THIS handler's own returned promise reject, and that rejection becomes `previous` for
          // this key's next write - which then skips its POST entirely, chained onto a promise
          // that already rejected, failing silently forever (Codex review, PR #1090).
          try {
            addAlert(
              mkAlert({
                title: t("preferences.saveFailedTitle"),
                message: t("preferences.saveFailedMessage"),
                variant: "warning",
              }),
            );
          } catch (alertError) {
            console.error("Could not raise the preference-save-failed alert", alertError);
          }
        }),
      );
    },
  ];
}
