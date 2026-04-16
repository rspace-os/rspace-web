import React from "react";
import { createRoot, type Root } from "react-dom/client";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "@/theme";
import UserDetails from "./UserDetails";

declare global {
  interface RSGlobal {
    trackEvent?: (event: string, properties?: Record<string, unknown>) => void;
  }

  interface Window {
    ___UserDetailsEntrypointInitialised?: boolean;
    RS: RSGlobal;
  }
}

type Position = ["top" | "bottom", "right" | "left"];

const mountedRoots = new WeakMap<Element, Root>();

function getPosition(position?: string): Position {
  switch (position) {
    case "top_left":
      return ["top", "left"];
    case "top_right":
      return ["top", "right"];
    case "bottom_left":
      return ["bottom", "left"];
    case "bottom_right":
    default:
      return ["bottom", "right"];
  }
}

function buildFullName({
  fullName,
  firstName,
  lastName,
  username,
}: {
  fullName?: string;
  firstName?: string;
  lastName?: string;
  username?: string;
}): string {
  if (fullName) {
    return fullName;
  }

  const combinedName = [firstName, lastName].filter(Boolean).join(" ").trim();
  if (combinedName) {
    return combinedName;
  }

  return username ?? "User";
}

function buildLabel({
  display,
  username,
  fullName,
  firstName,
  lastName,
}: {
  display?: string;
  username?: string;
  fullName?: string;
  firstName?: string;
  lastName?: string;
}): string {
  if (display === "username" && username) {
    return username;
  }

  return buildFullName({ fullName, firstName, lastName, username });
}

function mountUserDetails(domContainer: Element): void {
  if (mountedRoots.has(domContainer)) {
    return;
  }

  const userId = Number.parseInt(
    domContainer.getAttribute("data-user-id") ?? "",
    10,
  );
  if (Number.isNaN(userId)) {
    return;
  }

  const username = domContainer.getAttribute("data-username") ?? undefined;
  const firstName = domContainer.getAttribute("data-first-name") ?? undefined;
  const lastName = domContainer.getAttribute("data-last-name") ?? undefined;
  const fullNameAttribute =
    domContainer.getAttribute("data-full-name") ?? undefined;
  const fullName = buildFullName({
    fullName: fullNameAttribute,
    firstName,
    lastName,
    username,
  });
  const label = buildLabel({
    display: domContainer.getAttribute("data-display") ?? undefined,
    username,
    fullName: fullNameAttribute,
    firstName,
    lastName,
  });
  const position = getPosition(
    domContainer.getAttribute("data-position") ?? undefined,
  );

  const root = createRoot(domContainer);
  mountedRoots.set(domContainer, root);
  root.render(
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <UserDetails
          userId={userId}
          fullName={fullName}
          label={label}
          position={position}
          variant="outlined"
          allowMessaging
          onOpen={() => {
            window.RS?.trackEvent?.("user:open:user_details_popover");
          }}
        />
      </ThemeProvider>
    </StyledEngineProvider>,
  );
}

function mountAllUserDetails(rootNode: ParentNode): void {
  if (rootNode instanceof Element && rootNode.matches(".user-details")) {
    mountUserDetails(rootNode);
  }

  rootNode.querySelectorAll(".user-details").forEach((element) => {
    mountUserDetails(element);
  });
}

function initialise(): void {
  if (!document.body) {
    throw new Error("UserDetailsEntrypoint: document.body is not available");
  }

  mountAllUserDetails(document.body);

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      mutation.addedNodes.forEach((node) => {
        if (node instanceof Element) {
          mountAllUserDetails(node);
        }
      });
    });
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true,
  });
}

if (!window.___UserDetailsEntrypointInitialised) {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialise, { once: true });
  } else {
    initialise();
  }

  window.___UserDetailsEntrypointInitialised = true;
}
