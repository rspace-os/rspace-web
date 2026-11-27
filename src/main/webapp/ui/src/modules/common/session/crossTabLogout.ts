const logoutKey = "rspace.logout";

declare global {
  interface Window {
    rspaceCrossTabLogoutStarted?: boolean;
  }
}

export function startCrossTabLogout() {
  if (window.rspaceCrossTabLogoutStarted || window.top !== window || location.pathname.startsWith("/public/")) {
    return;
  }
  window.rspaceCrossTabLogoutStarted = true;
  let lastHandledAt = Date.now() - performance.now();

  function handleLogout(payload: unknown) {
    if (
      typeof payload !== "object" ||
      payload === null ||
      !("at" in payload) ||
      typeof payload.at !== "number" ||
      !Number.isFinite(payload.at) ||
      payload.at <= lastHandledAt ||
      !("mode" in payload) ||
      (payload.mode !== "standalone" && payload.mode !== "sso")
    ) {
      return;
    }
    lastHandledAt = payload.at;
    try {
      sessionStorage.removeItem("id_token");
    } catch {
      // Storage may be unavailable. The session still ended on the server.
    }
    const proceed = window.dispatchEvent(new CustomEvent("rspace:session-ended", { cancelable: true }));
    if (proceed) {
      location.assign(payload.mode === "sso" ? "/public/ssologout" : "/login");
    }
  }

  function handleMarker(marker: string | null) {
    if (marker === null) return;
    let payload: unknown;
    try {
      payload = JSON.parse(marker);
    } catch {
      return;
    }
    handleLogout(payload);
  }

  function checkMarker() {
    let marker: string | null;
    try {
      marker = localStorage.getItem(logoutKey);
    } catch {
      return;
    }
    handleMarker(marker);
  }

  window.addEventListener("storage", (event) => {
    if (event.key === logoutKey && event.storageArea === localStorage) {
      handleMarker(event.newValue);
    }
  });
  if (typeof BroadcastChannel !== "undefined") {
    try {
      const channel = new BroadcastChannel("rspace-session");
      channel.addEventListener("message", (event: MessageEvent<unknown>) => {
        const message = event.data;
        if (typeof message === "object" && message !== null && "type" in message && message.type === "logout") {
          handleLogout(message);
        }
      });
    } catch {
      // Storage events and marker checks still work when the channel cannot open.
    }
  }
  window.addEventListener("pageshow", checkMarker);
  checkMarker();
}
