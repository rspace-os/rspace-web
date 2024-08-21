//@flow

import { type UseState } from "./types";
import React from "react";
import axios from "axios";
import * as Parsers from "./parsers";

/*
 * This constant ensures that we don't end up with clashing keys
 */
export const PREFERENCES: { [string]: symbol } = {
  GALLERY_VIEW_MODE: Symbol.for("GALLERY_VIEW_MODE"),
  GALLERY_SORT_BY: Symbol.for("GALLERY_SORT_BY"),
  GALLERY_SORT_ORDER: Symbol.for("GALLERY_SORT_ORDER"),
};

function fetchPreferences(): Promise<{ ... }> {
  return axios
    .get<{ ... }>("/userform/ajax/preference?preference=UI_JSON_SETTINGS")
    .then(({ data }) => data);
}

export default function useUiPreference(
  preference: $Values<typeof PREFERENCES>,
  opts: {|
    defaultValue: mixed,
  |}
): UseState<mixed> {
  const [value, setValue] = React.useState(opts.defaultValue);

  React.useEffect(() => {
    void fetchPreferences().then((data) => {
      const key = Symbol.keyFor(preference);
      if (!key) return;
      Parsers.getValueWithKey(key)(data).do((v) => {
        setValue(v);
      });
    });
  }, []);

  return [
    value,
    (newValue) => {
      setValue(newValue);

      const key = Symbol.keyFor(preference);
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
