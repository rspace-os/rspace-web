import React from "react";

type NoopTransitionProps = {
  in?: boolean;
  children?: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
  // React 19: ref is a regular prop (no forwardRef needed).
  ref?: React.Ref<HTMLElement>;
};

/**
 * A no-op MUI transition slot: renders its child immediately (no animation)
 * when `in` is true, forwarding ref/className/style and setting tabIndex={-1}
 * so focus management still works. Used as the `transition` slot in tests (and
 * anywhere an instant, animation-free transition is wanted) so dialogs/menus
 * appear synchronously rather than waiting on a timed animation.
 */
function NoopTransition({ in: inProp, children, className, style, ref }: NoopTransitionProps): React.ReactNode {
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
}

export default NoopTransition;
