import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import AlertContext from "../../../../stores/contexts/Alert";
import { Optional } from "../../../../util/optional";
import Clustermarket from "../Clustermarket";

import "@/__tests__/__mocks__/matchMedia";

/*
 * Clustermarket shares ../useDisconnect with the OMERO card, so these cover the
 * basePath it passes to that hook. Without them a typo there would break
 * Clustermarket while every OMERO test stayed green.
 */
describe("Clustermarket", () => {
  test("Disconnecting deletes the stored connection.", async () => {
    const user = userEvent.setup();
    const deleted = vi.fn();
    server.use(
      http.delete("/apps/clustermarket/connect", () => {
        deleted();
        return new HttpResponse(null, { status: 200 });
      }),
    );

    render(
      <Clustermarket
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.present("MASKED") },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    await user.click(screen.getByRole("button", { name: "apps:actions.disconnect" }));

    await waitFor(() => expect(deleted).toHaveBeenCalledOnce());
    expect(await screen.findByRole("button", { name: "apps:actions.connect" })).toBeVisible();
  });

  test("A failed disconnect leaves the card connected.", async () => {
    const user = userEvent.setup();
    const addAlert = vi.fn();
    server.use(http.delete("/apps/clustermarket/connect", () => new HttpResponse(null, { status: 500 })));

    render(
      <AlertContext.Provider value={{ addAlert, removeAlert: () => {} }}>
        <Clustermarket
          integrationState={{
            mode: "DISABLED",
            credentials: { ACCESS_TOKEN: Optional.present("MASKED") },
          }}
          update={() => {}}
        />
      </AlertContext.Provider>,
    );

    await user.click(screen.getByRole("button"));
    await user.click(screen.getByRole("button", { name: "apps:actions.disconnect" }));

    // wait for the 500 to be fully handled first: the Disconnect button is present from the
    // start, so asserting on it immediately would pass even without the failure guard
    await waitFor(() => expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error" })));

    expect(screen.getByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "apps:actions.connect" })).not.toBeInTheDocument();
  });
});
