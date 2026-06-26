import React from "react";
import { vi } from "vitest";

type TransitionChild = React.ReactNode | ((state: string, childProps: Record<string, unknown>) => React.ReactNode);

type TransitionProps = {
  in?: boolean;
  children?: TransitionChild;
  // React 19: ref is a regular prop (no forwardRef needed).
  ref?: React.Ref<unknown>;
};

type TransitionGroupProps = {
  children?: React.ReactNode;
};

function TransitionMock({ in: inProp, children, ref }: TransitionProps): React.ReactNode {
  if (inProp === false) {
    return null;
  }
  if (typeof children === "function") {
    return children("entered", {}) as React.ReactElement | null;
  }
  if (React.isValidElement(children)) {
    return React.cloneElement(children as React.ReactElement<Record<string, unknown>>, { ref });
  }
  return children ?? null;
}

vi.mock("@mui/material/Grow", () => ({
  __esModule: true,
  default: TransitionMock,
}));

vi.mock("@mui/material/Fade", () => ({
  __esModule: true,
  default: TransitionMock,
}));

vi.mock("react-transition-group", () => ({
  Transition: ({ children, in: inProp }: TransitionProps) =>
    typeof children === "function" ? children(inProp ? "entered" : "exited", {}) : (children ?? null),
  CSSTransition: ({ children }: TransitionGroupProps) => children ?? null,
  TransitionGroup: ({ children }: TransitionGroupProps) => children ?? null,
}));
