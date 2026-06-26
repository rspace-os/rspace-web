import React from "react";

type NoopTransitionProps = {
  in?: boolean;
  children?: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
};

/**
 * A no-op MUI transition slot: renders its child immediately (no animation)
 * when `in` is true, forwarding ref/className/style and setting tabIndex={-1}
 * so focus management still works. Used as the `transition` slot in tests (and
 * anywhere an instant, animation-free transition is wanted) so dialogs/menus
 * appear synchronously rather than waiting on a timed animation.
 *
 * NOTE: still uses forwardRef. forwardRef is soft-deprecated in React 19 but
 * remains functional; migrating MUI transition slots to ref-as-prop is a
 * behaviour-sensitive change deferred until the React Compiler is enabled.
 */
const NoopTransition = React.forwardRef<HTMLElement, NoopTransitionProps>(
  ({ in: inProp, children, className, style }, ref) => {
    if (!inProp) return null;
    if (React.isValidElement(children)) {
      return React.cloneElement(children as React.ReactElement<Record<string, unknown>>, {
        ref,
        className,
        style,
        tabIndex: -1,
      });
    }
    return children ?? null;
  },
);
NoopTransition.displayName = "NoopTransition";

export default NoopTransition;
