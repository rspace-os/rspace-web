import MuiDialog from "@mui/material/Dialog";
import MuiDrawer from "@mui/material/Drawer";
import MuiMenu from "@mui/material/Menu";
import React, { createContext, Suspense, useContext, useRef } from "react";

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
 * Each of these components also wraps its children in a local Suspense
 * boundary. i18next runs with `useSuspense: true` and lazily loaded
 * namespaces, so the first `useTranslation` for a namespace the page did not
 * preload suspends. Without a boundary here that suspension reaches the page's
 * `I18nRoot`, which replaces the *entire page* with its fallback -- clicking
 * the Gallery's Create button blanked the whole Gallery for ~540ms behind a
 * spinner, because the DMP menu items pull the `apps` namespace and the Gallery
 * only preloads `gallery`, `common` and `about`. Overlays are how most
 * interaction-revealed UI appears, so containing the suspension here fixes the
 * class rather than each call site: a briefly empty menu or dialog body is a
 * far better failure mode than a blank page.
 *
 * The boundary also sits below the Modal's own transition, so a suspension in
 * the content cannot disconnect that transition's effects -- the mechanism
 * behind the stranded modals in PRT-1118 and PRT-1135. That is defence in depth
 * behind the @mui/material 9.3.1 fix rather than a substitute for it.
 *
 * And that it's it. The fact that a context is being used is purely an
 * implementation detail. That's why its declared inside this module and not
 * in `../stores/contexts` -- the rest of the codebase does not need to be
 * concerned with how this works.
 */

type DialogBoundaryContextType = {
  modalContainer: { current: HTMLElement | null };
};

const DEFAULT_DIALOG_BOUNDARY_CONTEXT: DialogBoundaryContextType = {
  modalContainer: { current: null },
};

const DialogBoundaryContext: React.Context<DialogBoundaryContextType> = createContext(DEFAULT_DIALOG_BOUNDARY_CONTEXT);

/**
 * This component defines a <div> into which all UI components exported by this
 * module will be rendered within. By default, the UI components are added to
 * the <body> element of the document but with this component they will be
 * rendered as children of this <div> instead.
 */
export function DialogBoundary({ children }: { children: React.ReactNode }): React.ReactNode {
  const modalContainer = useRef<HTMLDivElement | null>(null);
  return (
    <div ref={modalContainer}>
      <DialogBoundaryContext.Provider value={{ modalContainer }}>{children}</DialogBoundaryContext.Provider>
    </div>
  );
}

/*
 * Nested modals share one body scroll lock.
 *
 * Each of Dialog/Menu/Drawer previously ran its own effect that set
 * `document.body.style.overflow` to "hidden" while open and "unset" while
 * closed, with no cleanup. Consequences, all real: nested modals fought over
 * the value, closing an inner one unlocked the page while an outer one was
 * still open, "unset" clobbered whatever the page had set rather than restoring
 * it, and a modal unmounted while open left the page permanently unscrollable.
 *
 * RSDEV-1317 suggests deleting the effect outright, on the grounds that MUI
 * locks scrolling itself. That does not hold here: MUI locks the scroll
 * container derived from the modal's `container` prop, and these components
 * deliberately re-parent modals into the DialogBoundary div, so MUI locks that
 * div rather than the body. Measured on the Gallery with two modals open,
 * `document.body.style.overflow` was empty while MUI had applied
 * `overflow: hidden` to the boundary div. The Gallery itself is not scrollable
 * so nothing would break there, but the legacy JSP pages hosting ShareDialog,
 * RenameDialog, TagDialog and CompareDialog do scroll. So the lock is kept and
 * made correct instead: reference-counted across nested modals, restoring the
 * original value, and released on unmount.
 */
let scrollLockCount = 0;
let overflowBeforeLock: string | null = null;

function useBodyScrollLock(open: boolean | undefined): void {
  React.useEffect(() => {
    if (!open) return;
    if (scrollLockCount === 0) {
      overflowBeforeLock = document.body.style.overflow;
      document.body.style.overflow = "hidden";
    }
    scrollLockCount += 1;
    return () => {
      scrollLockCount -= 1;
      if (scrollLockCount === 0) {
        document.body.style.overflow = overflowBeforeLock ?? "";
        overflowBeforeLock = null;
      }
    };
  }, [open]);
}

/*
 * Why the container is handed over as a *stable* getter, and why RSDEV-1317
 * phase 5 (state plus a callback ref) is not used.
 *
 * MUI's Portal resolves `container` inside an effect keyed on the prop itself.
 * The three shapes behave very differently here:
 *
 *   1. An inline `() => ref.current` arrow, as this file had. New identity
 *      every render, so the effect re-runs constantly and the mount node can
 *      move out from under an open modal -- the mui/material-ui#32286 territory
 *      behind PRT-1118 and PRT-1135.
 *   2. State plus a callback ref (phase 5). The container goes null -> div
 *      deterministically, so the portal always relocates once. Where a boundary
 *      is already mounted that is harmless, but where a boundary and an ALREADY
 *      OPEN dialog mount in the same commit the dialog portals to
 *      document.body, ModalManager aria-hides the other body children, and the
 *      relocation then moves the dialog inside that hidden subtree. Measured
 *      when trying it: 42 failures across ShareDialog, pubchem/ImportDialog and
 *      FieldmarkImportDialog, all "Unable to find role=dialog".
 *   3. The stable getter below. React attaches the boundary div's ref after its
 *      descendants' layout effects, so a same-commit dialog reads null and
 *      portals to document.body -- and because the prop identity never changes,
 *      the effect does not re-run and it STAYS there: reachable, no relocation.
 *      Where the boundary mounted earlier (the normal case, <Alerts> at app
 *      level) the first read already returns the div, so toasts still render
 *      above dialogs as intended.
 *
 * Note the same-commit shape is not limited to a nested <DialogBoundary>, which
 * is all RSDEV-1317 groups A and B removed: any tree mounting <Alerts> together
 * with an open dialog has it too, because Alerts contains a boundary.
 */
function useStableContainerGetter(): () => HTMLElement | null {
  const { modalContainer } = useContext(DialogBoundaryContext);
  return React.useCallback(() => modalContainer.current, [modalContainer]);
}

/**
 * A Dialog that is rendered within the boundary defined by DialogBoundary.
 *
 * If one of the descendents of the Dialog is not a Material UI DialogTitle
 * then vi-axe will rightly complain that the dialog does not have a label.
 * Instead of passing `aria-label` or `aria-labelledby` here, be sure to use
 * a DialogTitle as the Material UI Dialog and DialogTitle already contain
 * the logic for wiring up the `aria-labelledby` attribute correctly.
 */
export function Dialog(props: Omit<React.ComponentProps<typeof MuiDialog>, "container">): React.ReactNode {
  const getModalContainer = useStableContainerGetter();
  const { children, open, ...rest } = props;

  useBodyScrollLock(open);

  return (
    <MuiDialog container={getModalContainer} open={open} {...rest}>
      <Suspense fallback={null}>{children}</Suspense>
    </MuiDialog>
  );
}

/**
 * A Menu that is rendered within the boundary defined by DialogBoundary.
 */
export function Menu(props: Omit<React.ComponentProps<typeof MuiMenu>, "container">): React.ReactNode {
  const getModalContainer = useStableContainerGetter();
  const { children, open, ...rest } = props;

  useBodyScrollLock(open);

  return (
    <MuiMenu container={getModalContainer} open={open} {...rest}>
      <Suspense fallback={null}>{children}</Suspense>
    </MuiMenu>
  );
}

/**
 * A Drawer that is rendered within the boundary defined by DialogBoundary.
 */
export function Drawer(props: Omit<React.ComponentProps<typeof MuiDrawer>, "container">): React.ReactNode {
  const getModalContainer = useStableContainerGetter();
  const { children, open, ...rest } = props;

  useBodyScrollLock(open);

  return (
    <MuiDrawer
      /*
       * Only temporary drawers are modal and require the dialog boundary.
       * Including the superfluous prop otherwise results in a console error.
       * See https://mui.com/material-ui/api/drawer/
       */
      {...(props.variant === "temporary" ? { container: getModalContainer } : {})}
      open={open}
      {...rest}
    >
      <Suspense fallback={null}>{children}</Suspense>
    </MuiDrawer>
  );
}
