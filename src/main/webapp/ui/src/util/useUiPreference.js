//@flow

import { type UseState } from "./types";

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
  return ["tree", () => {}];
}
