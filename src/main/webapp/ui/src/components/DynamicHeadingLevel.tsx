import Typography from "@mui/material/Typography";
import React from "react";

/**
 * An accessibility best practice is to have all headings on a web page descend
 * in order without skipping any levels. This rule restricts the re-use of
 * react components that render HTMLHeadingElements as the component can only
 * be used in other parts of the UI at the same heading depth. For example, a
 * table designed to be rendered beneath a h2 tag, and thus renders its own h3
 * tag, cannot be used on a different page beneath a h4 tag as it will render a
 * h3 inside of a h4.
 *
 * This component solves this problem by allowing for components to render a
 * heading without specifying its level, with the level of headings simply
 * being one level deeper than the headings above it. This is achieved with two
 * pieces
 *    - HeadingContext, which defines a react context within which headings
 *      will be rendered at a particular level.
 *    - Heading, a component that renders headings at the level specified by
 *      the context.
 *
 * For example,
 * ```
 *   <HeadingContext>
 *     <Heading>Some top-level heading</Heading>
 *     <HeadingContext>
 *       <p>Intro paragraph</p>
 *       <Heading>Some sub-heading</Heading>
 *       <HeadingContext>
 *         <SomeComponentThatRendersMoreContextWithHeadings />
 *       </HeadingContext>
 *     </HeadingContext>
 *   </HeadingContext>
 * ```
 */

type HeadingContextType = {
  level: 1 | 2 | 3 | 4 | 5 | 6;
};

const DEFAULT_HEADING_CONTEXT: HeadingContextType = {
  level: 1,
};

const HContext: React.Context<HeadingContextType> = React.createContext(
  DEFAULT_HEADING_CONTEXT
);

export function HeadingContext({
  children,
  level: overridenLevel,
}: {
  /**
   * More components that render headings using Heading
   */
  children: React.ReactNode;

  /**
   * The level of the context can be manually specified, allowing for this
   * system to be used on pages that are also manually rendering
   * HTMLHeadingElements. The root HeadingContext can specify a level and all
   * headings inside with descend from there. This MUST NOT be specified on
   * HeadingContexts that are not the root.
   * ```
   *   <h1>Heading</h1>
   *   <HeadingContext level={2}>
   *     <Heading>This will be a h2</Heading>
   *     <HeadingContext>
   *       <SomeComponentThatRendersMoreContextWithHeadings />
   *     </HeadingContext>
   *   </HeadingContext>
   * ```
   */
  level?: 1 | 2 | 3 | 4 | 5 | 6;
}): React.ReactNode {
  const { level } = React.useContext(HContext);

  if (typeof overridenLevel !== "undefined" && level > 1)
    throw new Error(
      "Only root HeadingContexts can specify a level. Skipping heading levels and reseting to a lower level are both accessibility violations"
    );

  const l = overridenLevel ?? Math.min(6, level + 1);
  if (l !== 1 && l !== 2 && l !== 3 && l !== 4 && l !== 5 && l !== 6)
    throw new Error("impossible");

  return (
    <HContext.Provider
      value={{
        level: l,
      }}
    >
      {children}
    </HContext.Provider>
  );
}

export function Heading({
  children,
  variant,
  className,
  id,
  sx,
}: {
  children: React.ReactNode;
  variant?: "h1" | "h2" | "h3" | "h4" | "h5" | "h6";
  className?: string;
  id?: string;
  sx?: object;
}): React.ReactNode {
  const { level } = React.useContext(HContext);
  const v = variant ?? `h${level}`;

  if (level === 1)
    return (
      <Typography
        variant={v}
        component="h1"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
  if (level === 2)
    return (
      <Typography
        variant={v}
        component="h2"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
  if (level === 3)
    return (
      <Typography
        variant={v}
        component="h3"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
  if (level === 4)
    return (
      <Typography
        variant={v}
        component="h4"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
  if (level === 5)
    return (
      <Typography
        variant={v}
        component="h5"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
  if (level === 6)
    return (
      <Typography
        variant={v}
        component="h6"
        className={className}
        id={id}
        sx={sx}
      >
        {children}
      </Typography>
    );
}
