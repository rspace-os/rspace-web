import { test, expect } from "@playwright/experimental-ct-react";
import { type Locator } from "@playwright/test";
import ErrorBoundary from "./components/ErrorBoundary";
import React from "react";
import ErrorComponent from "./App.story";

test("should work", async ({
  mount,
}: {
  mount: (jsx: React.ReactElement) => Promise<Locator>;
}) => {
  const component = await mount(<h1>Hello World</h1>);
  await expect(component).toContainText("Hello World");
});

test("should catch error", async ({
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
