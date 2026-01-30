import React from "react";
import { vi } from "vitest";

type TransitionChild =
  | React.ReactNode
  | ((state: string, childProps: Record<string, unknown>) => React.ReactNode);

type TransitionProps = {
  in?: boolean;
  children?: TransitionChild;
};

type TransitionGroupProps = {
  children?: React.ReactNode;
};

const TransitionMock = React.forwardRef<unknown, TransitionProps>(
  ({ in: inProp, children }, ref) => {
    if (inProp === false) {
      return null;
    }
    if (React.isValidElement(children)) {
      return React.cloneElement(children, { ref });
    }
    return children ?? null;
  },
);
TransitionMock.displayName = "TransitionMock";

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
    typeof children === "function"
      ? children(inProp ? "entered" : "exited", {})
      : children ?? null,
  CSSTransition: ({ children }: TransitionGroupProps) => children ?? null,
  TransitionGroup: ({ children }: TransitionGroupProps) => children ?? null,
}));
