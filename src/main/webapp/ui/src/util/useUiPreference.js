//@flow

import { type UseState } from "./types";
import React from "react";

/*
 * This constant ensures that we don't end up with clashing keys
 */
export const PREFERENCES: { [Preferences]: symbol } = {
  GALLERY_VIEW_MODE: Symbol("GALLERY_VIEW_MODE"),
};

export default function useUiPreference(
  preference: $Values<typeof PREFERENCES>,
  opts: {|
    defaultValue: mixed,
  |}
): UseState<mixed> {
  const [value, setValue] = React.useState(opts.defaultValue);

  // fetch from server

  return [
    value,
    (newValue) => {
      setValue(newValue);
      // update server
    },
  ];
}
