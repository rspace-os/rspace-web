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
};

type UiPreferencesContextType = {[key in keyof (typeof PREFERENCES)]: mixed};

const DEFAULT_UI_PREFERENCES_CONTEXT: UiPreferencesContextType = mapObject(
  () => null,
  PREFERENCES
);

const UiPreferencesContext: Context<UiPreferencesContextType> = React.createContext(
  DEFAULT_UI_PREFERENCES_CONTEXT
);

function fetchPreferences(): Promise<{ ... }> {
  return axios
    .get<UiPreferencesContextType>("/userform/ajax/preference?preference=UI_JSON_SETTINGS")
    .then(({ data }) => data);
}

export function UiPreferences({ children }: {| children: Node |}): Node {
  const [uiPreferences, setUiPreferences] = React.useState<UiPreferencesContextType | null>(null);

  React.useEffect(() => {
    void fetchPreferences().then((data) => {
      setUiPreferences(data);
    });
  }, []);

  if (!uiPreferences) return null;
  return (
    <UiPreferencesContext.Provider value={uiPreferences}>
      {children}
    </UiPreferencesContext.Provider>
  );
}

export default function useUiPreference<T>(
  preference: $Values<typeof PREFERENCES>,
  opts: {|
    defaultValue: T,
  |}
): UseState<T> {
  const uiPreferences = React.useContext(UiPreferencesContext);
  const key = Symbol.keyFor(preference);
  let v = opts.defaultValue;
  if (key && uiPreferences[key]) {
    // $FlowExpectedError[incompatible-type] We assume the server responds with the right type
    v = uiPreferences[key];
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
            ...preferences,
            [key]: newValue,
          })
        );
        await axios.post<FormData, mixed>(
          "/userform/ajax/preference",
          formData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
            },
          }
        );
      })();
    },
  ];
}
