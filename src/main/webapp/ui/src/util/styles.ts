import * as trm from "tss-react/mui";
import { type ComponentType } from "react";
import { type Theme } from "../theme";
import { CSSObject } from "tss-react/types";

export function withStyles<Config extends object, Classes extends object>(
  styles:
    | { [Property in keyof Classes]: CSSObject }
    | ((
        theme: Theme,
        config: Config
      ) => { [Property in keyof Classes]: CSSObject })
): (c: ComponentType<Config & { classes: Classes }>) => ComponentType<Config> {
  return (
    component: ComponentType<Config & { classes: Classes }>
  ): ComponentType<Config> =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    trm.withStyles(component, styles as any) as ComponentType<Config>;
}

export type Sx =
  | {
      [cssProp: string]: string | number | ((t: Theme) => string | number) | Sx;
    }
  | ((
      theme: Theme
    ) => object | Array<boolean | object | ((t: Theme) => object)>);

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
export function mergeThemes(
  themeA: object | undefined,
  themeB: object | undefined
): object | undefined {
  if (typeof themeA === "undefined") return themeB;
  if (typeof themeB === "undefined") return themeA;
  if (typeof themeA === "object" && typeof themeB === "object") {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const merged: { [key: string]: any } = {};
    const keys = [...Object.keys(themeA), ...Object.keys(themeB)];
    for (const k of keys) {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-argument, @typescript-eslint/no-explicit-any, @typescript-eslint/no-unsafe-member-access
      merged[k] = mergeThemes((themeA as any)[k], (themeB as any)[k]);
    }
    return merged;
  }
  return themeB;
}
