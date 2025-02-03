//@flow strict

import React, {
  type Node,
  type Context,
  type ComponentType,
  createContext,
  useContext,
  useRef,
} from "react";
import MuiDialog from "@mui/material/Dialog";
import MuiMenu from "@mui/material/Menu";
import MuiDrawer from "@mui/material/Drawer";

/**
 * This file contains a number of components that collectively provide a
 * mechanism for ensuring that UI elements that visually float above open
 * dialogs (such as toast alerts) are available to accessibility technologies
 * such as screen readers and braille keyboards.
 *
 * At the heart of modal dialogs in MUI, is the
 * [Portal component](https://mui.com/base-ui/react-portal/), an abstraction
 * over the `createPortal` react API which allows for DOM nodes to be rendered
 * outside of the point at which they are created in the component tree. This
 * means that by default, when a Dialog is opened it is appended to
 * `document.body` as a sibling node of the rest of the app.
 *
 * MUI then goes on to make all of the sibling nodes inaccessible to
 * accessibility technologies by adding the `aria-hidden` attribute set to
 * `true`; the code for which lives in https://github.com/mui/material-ui/blob/b7fea89bc232622546b6bc9675a818bfa95a8376/packages/mui-base/src/unstable_useModal/ModalManager.ts#L59
 * This is done to aid with accessiblity by preventing the user of such tools
 * from being able to reach the rest of the page, just as a sighted user
 * cannot until the dialog has been closed. It is this mechanism that
 * implemented the modal nature of dialogs for users using accessibility
 * technologies.
 *
 * However, this means that other parts of the UI that we would like to be
 * always available, such as the alerting mechanism that displays toasts in
 * the top right corner, are not reachable by these accessibility technologies
 * meaning that if an action inside a dialog triggers an alert, the user of
 * such tools will not know that such an alert has been triggered as it is
 * within the scope of `aria-hidden: true`.
 *
 * To resolve this, the code in this file moves where dialogs are rendered from
 * `document.body` to within a div inside of the application. Other UI
 * elements, such as those alerts, can then be rendered as siblings of this
 * parent container div and will then not be subject to the code that applies
 * the `aria-hidden: true`. This is done by declaring a DialogBoundary, which
 * is simply a div with a ref that is placed in a context, and a UI component
 * (e.g. dialog, menu, etc.) that pulls the div from the context and uses it to
 * set the `container` property. At the root of the application, UI elements
 * that should always float above the dialogs should then be rendered as
 * siblings of the DialogBoundary.
 * ```
 * function App() {
 *   return (
 *     <>
 *       <DialogBoundary>{restOfApp}</DialogBoundary>
 *       <AlertSystem />
 *     </>
 *   );
 * }
 * ```
 * Then, when a UI component is needed in the code, simply use the UI component
 * exported from this module rather than the one exported by MUI. In all other
 * respects, they behave exactly the same.
 *
 * And that it's it. The fact that a context is being used is purely an
 * implementation detail. That's why its declared inside this module and not
 * in `../stores/contexts` -- the rest of the codebase does not need to be
 * concerned with how this works.
 */

type DialogBoundaryContextType = {|
  modalContainer: {| current: HTMLElement | null |},
|};

const DEFAULT_DIALOG_BOUNDARY_CONTEXT: DialogBoundaryContextType = {
  modalContainer: { current: null },
};

const DialogBoundaryContext: Context<DialogBoundaryContextType> = createContext(
  DEFAULT_DIALOG_BOUNDARY_CONTEXT
);

/**
 * This component defines a <div> into which all UI components exported by this
 * module will be rendered within. By default, the UI components are added to
 * the <body> element of the document but with this component they will be
 * rendered as children of this <div> instead.
 */
export function DialogBoundary({ children }: {| children: Node |}): Node {
  const modalContainer = useRef<HTMLElement | null>(null);
  return (
    <div ref={modalContainer}>
      <DialogBoundaryContext.Provider value={{ modalContainer }}>
        {children}
      </DialogBoundaryContext.Provider>
    </div>
  );
}

// everything from MuiDialog except container
type DialogArgs<T> = {|
  onClose: () => void,
  open: boolean,
  maxWidth?: "sm" | "md" | "lg" | "xl",
  fullWidth?: boolean,
  fullScreen?: boolean,
  children: Node,
  style?: { ... },
  classes?: {|
    paper?: string,
  |},
  TransitionComponent?: ComponentType<T> | Node,
  className?: string,
  onClick?: () => void,

  /*
   * If one of the descendents of the Dialog is not a Material UI DialogTitle
   * then jest-axe will rightly complain that the dialog does not have a label.
   * Instead of passing `aria-label` or `aria-labelledby` here, be sure to use
   * a DialogTitle as the Material UI Dialog and DialogTitle already contain
   * the logic for wiring up the `aria-labelledby` attribute correctly.
   */
|};

/**
 * A Dialog that is rendered within the boundary defined by DialogBoundary.
 */
export function Dialog<T>(props: DialogArgs<T>): Node {
  const { modalContainer } = useContext(DialogBoundaryContext);
  const { children, open, ...rest } = props;

  React.useEffect(() => {
    if (document.body) {
      if (open) {
        document.body.style.overflow = "hidden";
      } else {
        document.body.style.overflow = "unset";
      }
    }
  }, [open]);

  return (
    <MuiDialog container={() => modalContainer.current} open={open} {...rest}>
      {children}
    </MuiDialog>
  );
}

// everything from MuiMenu except container
type MenuArgs = {|
  onClose: () => void,
  open: boolean,
  anchorEl: EventTarget | null,
  children: Node,
  MenuListProps?: { ... },
  anchorOrigin?: {|
    vertical: "top" | "center" | "bottom",
    horizontal: "left" | "center" | "right",
  |},
  transformOrigin?: {|
    vertical: "top" | "center" | "bottom",
    horizontal: "left" | "center" | "right",
  |},
  id?: string,
  sx?: { ... },
|};

/**
 * A Menu that is rendered within the boundary defined by DialogBoundary.
 */
export function Menu(props: MenuArgs): Node {
  const { modalContainer } = useContext(DialogBoundaryContext);
  const { children, open, ...rest } = props;

  React.useEffect(() => {
    if (document.body) {
      if (open) {
        document.body.style.overflow = "hidden";
      } else {
        document.body.style.overflow = "unset";
      }
    }
  }, [open]);

  return (
    <MuiMenu container={() => modalContainer.current} open={open} {...rest}>
      {children}
    </MuiMenu>
  );
}

// everything from MuiDrawer except container
type DrawerArgs = {|
  children: Node,
  open: boolean,
  anchor: "left",
  variant: "temporary" | "permanent",
  onClose: () => void,
  role: "region",
  "aria-label": string,
  id: string,
|};

/**
 * A Drawer that is rendered within the boundary defined by DialogBoundary.
 */
export function Drawer(props: DrawerArgs): Node {
  const { modalContainer } = useContext(DialogBoundaryContext);
  const { children, open, ...rest } = props;

  React.useEffect(() => {
    if (document.body) {
      if (open) {
        document.body.style.overflow = "hidden";
      } else {
        document.body.style.overflow = "unset";
      }
    }
  }, [open]);

  return (
    <MuiDrawer container={() => modalContainer.current} open={open} {...rest}>
      {children}
    </MuiDrawer>
  );
}
