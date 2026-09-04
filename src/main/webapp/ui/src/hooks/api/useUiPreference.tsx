import { mapValues } from "es-toolkit";
import React from "react";
import axios from "@/common/axios";
import type { UseState } from "../../util/types";

/**
 * This constant ensures that we don't end up with clashing keys
 */
export const PREFERENCES: { [pref: string]: symbol } = {
  GALLERY_VIEW_MODE: Symbol.for("GALLERY_VIEW_MODE"),
  GALLERY_SORT_BY: Symbol.for("GALLERY_SORT_BY"),
  GALLERY_SORT_ORDER: Symbol.for("GALLERY_SORT_ORDER"),
  GALLERY_PICKER_INITIAL_SECTION: Symbol.for("GALLERY_PICKER_INITIAL_SECTION"),
  GALLERY_SIDEBAR_OPEN: Symbol.for("GALLERY_SIDEBAR_OPEN"),
  INVENTORY_FORM_SECTIONS_EXPANDED: Symbol.for("INVENTORY_FORM_SECTIONS_EXPANDED"),
  INVENTORY_HIDDEN_RIGHT_PANEL: Symbol.for("INVENTORY_HIDDEN_RIGHT_PANEL"),
  // The single per-process "remember" bundle (template + documentation + collected values), keyed by
  // operation + process name. Supersedes the earlier per-item template/doc/amount default preferences.
  INVENTORY_OPERATION_PROCESS_VALUES: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES"),
  INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
  INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
  SYSADMIN_USERS_TABLE_COLUMNS: Symbol.for("SYSADMIN_USERS_TABLE_COLUMNS"),
};

type UiPreferencesContextType = {
  uiPreferences: { [key in keyof typeof PREFERENCES]: unknown };
  setUiPreferences: React.Dispatch<React.SetStateAction<{ [key in keyof typeof PREFERENCES]: unknown } | null>>;
  /*
   * Writes are chained so two writes of the same key from one page land in the order they were
   * made, and so a failure is reported once rather than per in-flight request. Losing a key to an
   * overlapping writer is no longer possible: each write sends only its own key and the server
   * merges it (code review, finding 3). The chain belongs to the provider rather than the module
   * so one stalled request cannot hold up every other preference write in the app for the rest of
   * the session.
   */
  pendingWrite: React.MutableRefObject<Promise<void>>;
};

const DEFAULT_UI_PREFERENCES_CONTEXT: UiPreferencesContextType = {
  uiPreferences: mapValues(PREFERENCES, () => null),
  setUiPreferences: () => {},
  pendingWrite: { current: Promise.resolve() },
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
  const pendingWrite = React.useRef<Promise<void>>(Promise.resolve());

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
    <UiPreferencesContext.Provider value={{ uiPreferences, setUiPreferences, pendingWrite }}>
      {children}
    </UiPreferencesContext.Provider>
  );
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
): UseState<T> {
  const { uiPreferences, setUiPreferences, pendingWrite } = React.useContext(UiPreferencesContext);
  const key = Symbol.keyFor(preference);
  let v = opts.defaultValue;
  if (key && typeof uiPreferences[key] !== "undefined") {
    v = (uiPreferences[key] as { value: T })?.value ?? opts.defaultValue;
  }
  const [value, setValue] = React.useState(v);

  return [
    value,
    (newValue) => {
      setValue(newValue);
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
      pendingWrite.current = pendingWrite.current.then(async () => {
        const formData = new FormData();
        formData.append("preference", "UI_JSON_SETTINGS");
        // Only this key is sent; the server merges it into the stored object under a row lock.
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
      // Caught so a failure cannot block the chain (callers never await it), but reported: a
      // silently dropped preference save is invisible to user and developer alike.
      pendingWrite.current = pendingWrite.current.catch((e) => {
        console.error(`Could not save UI preference ${key}`, e);
      });
    },
  ];
}
