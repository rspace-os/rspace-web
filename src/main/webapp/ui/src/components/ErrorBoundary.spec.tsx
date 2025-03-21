import { test, expect } from "@playwright/experimental-ct-react";
import { type Locator } from "@playwright/test";
import ErrorBoundary from "./ErrorBoundary";
import React from "react";
import { ErrorComponent } from "./ErrorBoundary.story";

test("When there is an error rendering one of its descendent components, ErrorBoundary should show an error message.", async ({
  mount,
}: {
  mount: (jsx: React.ReactElement) => Promise<Locator>;
}) => {
  const component = await mount(
    <ErrorBoundary message="Something went wrong.">
      <ErrorComponent />
    </ErrorBoundary>
  );
  await expect(component).toContainText("Something went wrong.");
});
