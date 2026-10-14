/*
 * Snackbars only starts listening in a useEffect, after React commits, so toasts
 * dispatched earlier (e.g. from jQuery document-ready handlers) would be lost.
 * Module scripts run before DOMContentLoaded, so ToastMessageEntrypoint
 * registers bufferPreMountToast at load to catch them; Snackbars drains the
 * buffer on mount.
 */
export const preMountToasts: Array<Event> = [];

export const bufferPreMountToast = (event: Event) => {
  preMountToasts.push(event);
};
