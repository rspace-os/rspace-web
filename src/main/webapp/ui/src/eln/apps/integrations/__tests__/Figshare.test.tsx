import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import { Optional } from "../../../../util/optional";
import Figshare from "../Figshare";

import "@/__tests__/__mocks__/matchMedia";
import { createIntegrationsClient, WithIntegrations } from "../../__tests__/integrationsQueryClient";

describe("Figshare", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const user = userEvent.setup();
      const { baseElement } = render(
        <WithIntegrations
          client={createIntegrationsClient({
            FIGSHARE: {
              mode: "DISABLED",
              credentials: {
                ACCESS_TOKEN: Optional.empty(),
              },
            },
          })}
        >
          <Figshare />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  test("Should have a connect button when the user is not authenticated.", async () => {
    const user = userEvent.setup();
    render(
      <WithIntegrations
        client={createIntegrationsClient({
          FIGSHARE: {
            mode: "DISABLED",
            credentials: {
              ACCESS_TOKEN: Optional.empty(),
            },
          },
        })}
      >
        <Figshare />
      </WithIntegrations>,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "apps:actions.connect" })).toBeVisible();
  });
  test("Should have a disconnect button when the user is authenticated.", async () => {
    const user = userEvent.setup();
    render(
      <WithIntegrations
        client={createIntegrationsClient({
          FIGSHARE: {
            mode: "DISABLED",
            credentials: {
              ACCESS_TOKEN: Optional.present("some token"),
            },
          },
        })}
      >
        <Figshare />
      </WithIntegrations>,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
  });
});
