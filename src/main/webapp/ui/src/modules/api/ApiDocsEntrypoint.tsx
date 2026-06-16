import React from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "@/components/ErrorBoundary";
import ApiDocsPage from "./components/ApiDocsPage";

/*
 * Entrypoint for the public API documentation page (/public/apiDocs). Scalar
 * owns the entire page chrome, so we deliberately do not wrap it in the app's
 * MUI theme, React Query, or Analytics providers. The page is anonymous, so
 * no session-dependent context is appropriate either.
 */
window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (!domContainer) {
    console.error("Could not find element with id 'app'");
    return;
  }
  createRoot(domContainer).render(
    <React.StrictMode>
      <ErrorBoundary>
        <ApiDocsPage />
      </ErrorBoundary>
    </React.StrictMode>,
  );
});
