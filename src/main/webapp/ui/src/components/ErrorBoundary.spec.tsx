import {
  test,
  expect,
  type MountResult,
} from "@playwright/experimental-ct-react";
import ErrorBoundary from "./ErrorBoundary";
import React from "react";
import { ErrorComponent } from "./ErrorBoundary.story";

test("When there is an error rendering one of its descendent components, ErrorBoundary should show an error message.", async ({
  mount,
}: {
  mount: (component: React.JSX.Element) => Promise<MountResult>;
}) => {
  const component = await mount(
    <ErrorBoundary message="Something went wrong.">
      <ErrorComponent />
    </ErrorBoundary>
  );
  await expect(component).toContainText("Something went wrong.");
});
