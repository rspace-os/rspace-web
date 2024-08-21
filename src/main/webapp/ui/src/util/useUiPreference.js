//@flow

import { type UseState } from "./types";
import React from "react";
import axios from "axios";

/*
 * This constant ensures that we don't end up with clashing keys
 */
export const PREFERENCES: { [Preferences]: symbol } = {
  GALLERY_VIEW_MODE: Symbol.for("GALLERY_VIEW_MODE"),
};

export default function useUiPreference(
  preference: $Values<typeof PREFERENCES>,
  opts: {|
    defaultValue: mixed,
  |}
): UseState<mixed> {
  const [value, setValue] = React.useState(opts.defaultValue);

  React.useEffect(() => {
    void axios
      .get("/userform/ajax/preference?preference=UI_JSON_SETTINGS")
      .then(({ data }) => {
        if (Symbol.keyFor(preference) in data)
          setValue(data[Symbol.keyFor(preference)]);
      });
  }, []);

  return [
    value,
    (newValue) => {
      setValue(newValue);

      const formData = new FormData();
      formData.append("preference", "UI_JSON_SETTINGS");
      formData.append(
        "value",
        JSON.stringify({
          [Symbol.keyFor(preference)]: newValue,
        })
      );
      void axios.post("/userform/ajax/preference", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
    },
  ];
}
