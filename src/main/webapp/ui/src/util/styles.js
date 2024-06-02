// @flow strict

import * as trm from "tss-react/mui";
import { type ComponentType } from "react";
import { type Theme } from "../theme";

export function withStyles<Config, Classes: { ... }>(
  styles:
    | $ObjMap<Classes, () => { ... }>
    | ((Theme, Config) => $ObjMap<Classes, () => { ... }>)
): (ComponentType<{| ...Config, classes: Classes |}>) => ComponentType<Config> {
  return function (
    component: ComponentType<{| ...Config, classes: Classes |}>
  ): ComponentType<Config> {
    return trm.withStyles(component, styles);
  };
}

export type Sx =
  | { ... }
  | ((Theme) => { ... } | Array<boolean | { ... } | ((Theme) => { ... })>);

/**
 * This function is for merging themes and only themes. It is not a truely
 * generic function for merging objects as it assumes that anything that has
 * type "object" is a simply object with string keys and values that are
 * strings, numbers, or other such simple objects. No checks are made for null,
 * Arrays, or instances of classes.
 *
 * Note that the order of the arguments is significant. The second will
 * override where both have non-object values for a particular key.
 */
export function mergeThemes(themeA: { ... }, themeB: { ... }): { ... } {
  if (typeof themeA === "undefined") return themeB;
  if (typeof themeB === "undefined") return themeA;
  if (typeof themeA === "object" && typeof themeB === "object") {
    const merged = {};
    const keys = [...Object.keys(themeA), ...Object.keys(themeB)];
    for (const k of keys) {
      merged[k] = mergeThemes(themeA[k], themeB[k]);
    }
    return merged;
  }
  return themeB;
}
