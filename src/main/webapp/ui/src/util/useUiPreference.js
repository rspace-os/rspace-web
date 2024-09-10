//@flow

import { type UseState } from "./types";
import React, { type Node, type Context } from "react";
import axios from "axios";
import { mapObject } from "./Util";

/*
 * This constant ensures that we don't end up with clashing keys
 */
export const PREFERENCES: { [string]: symbol } = {
  GALLERY_VIEW_MODE: Symbol.for("GALLERY_VIEW_MODE"),
  GALLERY_SORT_BY: Symbol.for("GALLERY_SORT_BY"),
  GALLERY_SORT_ORDER: Symbol.for("GALLERY_SORT_ORDER"),
  INVENTORY_FORM_SECTIONS_EXPANDED: Symbol.for("INVENTORY_FORM_SECTIONS_EXPANDED"),
  INVENTORY_HIDDEN_RIGHT_PANEL: Symbol.for("INVENTORY_HIDDEN_RIGHT_PANEL"),
};

type UiPreferencesContextType = {[key in keyof (typeof PREFERENCES)]: mixed};

const DEFAULT_UI_PREFERENCES_CONTEXT: UiPreferencesContextType = mapObject(
  () => null,
  PREFERENCES
);

const UiPreferencesContext: Context<UiPreferencesContextType> = React.createContext(
  DEFAULT_UI_PREFERENCES_CONTEXT
);

function fetchPreferences(): Promise<UiPreferencesContextType | ""> {
  return axios
    .get<UiPreferencesContextType | "">("/userform/ajax/preference?preference=UI_JSON_SETTINGS")
    .then(({ data }) => data);
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
export function UiPreferences({ children }: {| children: Node |}): Node {
  const [uiPreferences, setUiPreferences] = React.useState<UiPreferencesContextType | null>(null);

  React.useEffect(() => {
    void fetchPreferences().then((data) => {
      if (data === "") {
        setUiPreferences(mapObject(() => null, PREFERENCES));
        return;
      }
      setUiPreferences(data);
    }).catch(() => {
      setUiPreferences(mapObject(() => null, PREFERENCES));
    });
  }, []);

  /*
   * If it turns out that loading this data will likely take a while,
   * then we will want to replace this null with a loading spinner.
   */
  if (!uiPreferences) return null;
  return (
    <UiPreferencesContext.Provider value={uiPreferences}>
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
  preference: $Values<typeof PREFERENCES>,
  opts: {|
    defaultValue: T,
  |}
): UseState<T> {
  const uiPreferences = React.useContext(UiPreferencesContext);
  const key = Symbol.keyFor(preference);
  let v = opts.defaultValue;
  if (key && typeof uiPreferences[key] !== "undefined") {
    // $FlowExpectedError[incompatible-use] We assume the server responds with the right type
    v = uiPreferences[key]?.value ?? opts.defaultValue;
  }
  const [value, setValue] = React.useState(v);

  return [
    value,
    (newValue) => {
      setValue(newValue);

      if (!key) return;
      void (async () => {
        const preferences = await fetchPreferences();
        const formData = new FormData();
        formData.append("preference", "UI_JSON_SETTINGS");
        formData.append(
          "value",
          JSON.stringify({
            ...(typeof preferences === "object" ? preferences : {}),
            [key]: {
              value: newValue,
              // we save the time so that we have the option of implementing an
              // eviction polciy in the future
              time: new Date().getTime()
            },
          })
        );
        await axios.post<mixed, mixed>(
          "/userform/ajax/preference",
          formData
        );
      })();
    },
  ];
}
