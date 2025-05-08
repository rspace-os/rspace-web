import { action, observable, computed, makeObservable } from "mobx";
import { isMobile } from "react-device-detect";
import { type Alert } from "../contexts/Alert";
import type { RootStore } from "./RootStore";
import { match } from "../../util/Util";
import theme from "../../theme";
import React from "react";
import { type Panel } from "../../util/types";
import { pick } from "../../util/unsafeUtils";

type ConfirmationDialogProps = {
  title: Node;
  message: Node;
  yesLabel: string;
  noLabel: string;
  yes: () => void;
  no: () => void;
  confirmationSpinner: boolean;
};

const beforeUnloadAction = (e: Event & { returnValue: string }) => {
  e.preventDefault();
  e.returnValue = "";
};

const breakpoints: Record<"xs" | "sm" | "md" | "lg" | "xl", symbol> =
  Object.freeze({
    xs: Symbol("xs"),
    sm: Symbol("sm"),
    md: Symbol("md"),
    lg: Symbol("lg"),
    xl: Symbol("xl"),
  });

const isSingleColumnLayout = (
  viewportSize: (typeof breakpoints)[keyof typeof breakpoints]
) => [breakpoints.xs, breakpoints.sm].includes(viewportSize);

export default class UiStore {
  rootStore: RootStore;
  sidebarOpen: boolean = !isMobile;
  visiblePanel: "left" | "right" = "left";
  infoVisible: boolean = false;
  dialogVisiblePanel: "left" | "right" = "left";
  confirmPageNavigation: boolean = false;
  confirmationDialogProps: ConfirmationDialogProps | null = null;
  viewportSize: (typeof breakpoints)[keyof typeof breakpoints];
  dirty: boolean = false;
  discardChangesCallback: () => Promise<void>;

  /*
   * These properties keep a reference to the alert context that is used for
   * displaying alerts across the current webpage. This allows code that is
   * not inside the alert context, nor even within a react context at all
   * (such as the model classes) to display alerts. Where possible, it is
   * better to use the alert context directly as that will make the react
   * component easier to test and potentially reusable outside of Inventory.
   * For more info, see ../contexts/Alert
   */
  addAlert: (alert: Alert) => void = () => {};
  removeAlert: (alert: Alert) => void = () => {};

  /*
   * When batch editing, we don't want popups to appear for each record that the
   * user currently has a lock for. As such, this flag is intended to prevent
   * such dialogs whilst it is true.
   */
  recentBatchEditExpiryCheck: boolean | null = null;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      sidebarOpen: observable,
      visiblePanel: observable,
      infoVisible: observable,
      dialogVisiblePanel: observable,
      confirmPageNavigation: observable,
      confirmationDialogProps: observable,
      viewportSize: observable,
      dirty: observable,
      updateViewportSize: action,
      addAlert: observable,
      toggleSidebar: action,
      removeAlert: observable,
      setVisiblePanel: action,
      setDialogVisiblePanel: action,
      toggleInfo: action,
      setPageNavigationConfirmation: action,
      confirm: action,
      setDirty: action,
      unsetDirty: action,
      closeConfirmationDialog: action,
      isVerySmall: computed,
      isLarge: computed,
      isTouchDevice: computed,
      isSmall: computed,
      alwaysVisibleSidebar: computed,
    });
    this.rootStore = rootStore;
    window.addEventListener("resize", () => {
      const oldViewportSize = this.viewportSize;
      const wasSingleColumnLayout = isSingleColumnLayout(oldViewportSize);

      this.updateViewportSize();
      const isNowSingleColumnLayout = isSingleColumnLayout(this.viewportSize);

      if (!wasSingleColumnLayout && isNowSingleColumnLayout)
        this.toggleSidebar(false);
      if (wasSingleColumnLayout && !isNowSingleColumnLayout) {
        this.toggleSidebar(true);

        /*
         * Make the first search result the active result when transitioning
         * from a small viewport to a large one.
         */
        const { search } = this.rootStore.searchStore;
        if (!search.activeResult && search.filteredResults.length > 0) {
          void search.setActiveResult();
        }
      }
    });
    this.updateViewportSize();
    this.sidebarOpen = Object.values(
      pick("md", "lg", "xl")(breakpoints)
    ).includes(this.viewportSize);
  }

  updateViewportSize() {
    this.viewportSize = match<number, symbol>(
      ["xl", "lg", "md", "sm", "xs"].map((bp) => [
        (width) => width > theme.breakpoints.values[bp],
        breakpoints[bp],
      ])
    )(window.innerWidth);
  }

  get isVerySmall(): boolean {
    return this.viewportSize === breakpoints.xs;
  }

  get isLarge(): boolean {
    return (
      this.viewportSize === breakpoints.lg ||
      this.viewportSize === breakpoints.xl
    );
  }

  get isTouchDevice(): boolean {
    try {
      document.createEvent("TouchEvent");
      return true;
    } catch {
      return false;
    }
  }

  get isSmall(): boolean {
    return this.viewportSize === breakpoints.sm;
  }

  get alwaysVisibleSidebar(): boolean {
    return [
      breakpoints.sm,
      breakpoints.md,
      breakpoints.lg,
      breakpoints.xl,
    ].includes(this.viewportSize);
  }

  toggleSidebar(isOpen: boolean = !this.sidebarOpen): void {
    this.sidebarOpen = isOpen;
  }

  setVisiblePanel(panel: Panel) {
    this.visiblePanel = panel;
  }

  setDialogVisiblePanel(panel: Panel) {
    this.dialogVisiblePanel = panel;
  }

  toggleInfo(value: boolean = !this.infoVisible) {
    this.infoVisible = value;
    if (value) {
      this.rootStore.trackingStore.trackEvent("InfoPanelOpened");
    }
  }

  setPageNavigationConfirmation(value: boolean) {
    if (value) {
      if (!this.confirmPageNavigation) {
        window.addEventListener("beforeunload", beforeUnloadAction);
      }
    } else if (this.confirmPageNavigation) {
      window.removeEventListener("beforeunload", beforeUnloadAction);
    }
    this.confirmPageNavigation = value;
  }

  /*
   * A general-purpose confirmation function, designed to mimic the browser's
   * default `confirm` function, but with an asynchronous promise-based
   * interface. It requires that somewhere in the DOM lies the
   * ../../components/Confirm component which will display the confirmation
   * modal once it has been enabled by calling this method. Only one such
   * dialog can be shown at once, both due to a technical limitation and
   * because doing otherwise would be poor UX.
   */
  confirm(
    title: React.ReactNode,
    message: React.ReactNode,
    yesLabel: string = "OK",
    noLabel: string = "Cancel",

    /*
     * onConfirm is a callback function that callers of confirm can pass that
     * will perform some asynchronous action. Whilst that action is being
     * performed the confirmation dialog will stay visible and the "OK" button
     * will turn into a spinner. Once the promise returned by onConfirm
     * resolves, the promise returned by confirm also resolves with true.
     */
    onConfirm?: () => Promise<void>
  ): Promise<boolean> {
    if (!this.confirmationDialogProps) {
      return new Promise((resolve) => {
        this.confirmationDialogProps = {
          title,
          message,
          yesLabel,
          noLabel,
          yes: () => {
            const returnYes = action(() => {
              this.closeConfirmationDialog();
              resolve(true);
            });
            if (onConfirm) {
              this.confirmationDialogProps.confirmationSpinner = true;
              void onConfirm().then(returnYes);
            } else {
              returnYes();
            }
          },
          no: () => {
            this.closeConfirmationDialog();
            resolve(false);
          },
          confirmationSpinner: false,
        };
      });
    }
    throw new Error("Only one confirm dialog may be visible at once.");
  }

  setDirty(discardChangesCallback: () => Promise<void>) {
    /*
     * important that the callback calls unset function below
     * after cleaning up otherwise dirty flag will linger
     */
    this.dirty = true;
    this.discardChangesCallback = discardChangesCallback;
    this.setPageNavigationConfirmation(true);
  }

  unsetDirty() {
    this.dirty = false;
    this.discardChangesCallback = async () => {};
    this.setPageNavigationConfirmation(false);
  }

  confirmDiscardAnyChanges(): Promise<boolean> {
    // returns boolean -- true means the caller is free to navigate away from record
    if (this.dirty) {
      return this.confirm(
        "Leave the editor?",
        "Changes that you made will not be saved.",
        "Leave",
        "Cancel",
        () => this.discardChangesCallback()
      );
    }
    return Promise.resolve(true);
  }

  closeConfirmationDialog() {
    this.confirmationDialogProps = null;
  }
}
