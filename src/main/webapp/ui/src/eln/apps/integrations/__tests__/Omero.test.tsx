import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import AlertContext from "../../../../stores/contexts/Alert";
import { Optional } from "../../../../util/optional";
import Omero, { OMERO_CONNECTION_CHANNEL } from "../Omero";

import "@/__tests__/__mocks__/matchMedia";

describe("Omero", () => {
  test("Should have no axe violations.", async () => {
    const user = userEvent.setup();
    const { baseElement } = render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.empty() },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(await screen.findByRole("dialog")).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible();
  });
  test("Should render username and password fields.", async () => {
    const user = userEvent.setup();
    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.empty() },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("textbox", { name: "apps:integrations.omero.fields.username" })).toBeVisible();
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    expect(screen.getByLabelText("apps:integrations.omero.fields.password")).toBeVisible();
  });
  test("Connect form should submit without opening a blank tab first.", async () => {
    const user = userEvent.setup();
    const open = vi.spyOn(window, "open").mockReturnValue(null);
    try {
      render(
        <Omero
          integrationState={{
            mode: "DISABLED",
            credentials: { ACCESS_TOKEN: Optional.empty() },
          }}
          update={() => {}}
        />,
      );

      await user.click(screen.getByRole("button"));
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.omero.fields.username" }), "user");
      await user.type(screen.getByLabelText("apps:integrations.omero.fields.password"), "password");
      const form = screen.getByRole("form", { name: "apps:integrations.omero.credentialsFormLabel" });
      const submit = vi.fn((event: Event) => event.preventDefault());
      form.addEventListener("submit", submit);
      await user.click(screen.getByRole("button", { name: "apps:actions.connect" }));

      expect(form).toHaveAttribute("action", "/apps/omero/connect");
      expect(form).toHaveAttribute("method", "POST");
      expect(form).toHaveAttribute("target", "_blank");
      expect(submit).toHaveBeenCalledOnce();
      expect(open).not.toHaveBeenCalled();
    } finally {
      open.mockRestore();
    }
  });
  test("Should show a disconnect button when the user is already connected.", async () => {
    const user = userEvent.setup();
    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.present("MASKED") },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
    expect(screen.queryByRole("textbox", { name: "apps:integrations.omero.fields.username" })).not.toBeInTheDocument();
  });
  test("Disconnecting deletes the stored connection and restores the credentials form.", async () => {
    const user = userEvent.setup();
    const deleted = vi.fn();
    server.use(
      http.delete("/apps/omero/connect", () => {
        deleted();
        return new HttpResponse(null, { status: 200 });
      }),
    );

    render(
      <Omero
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
    expect(await screen.findByRole("form", { name: "apps:integrations.omero.credentialsFormLabel" })).toBeVisible();
  });

  test("The OMERO_CONNECTED broadcast marks the card as connected.", async () => {
    const user = userEvent.setup();
    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.empty() },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("form", { name: "apps:integrations.omero.credentialsFormLabel" })).toBeVisible();

    const channel = new BroadcastChannel(OMERO_CONNECTION_CHANNEL);
    try {
      channel.postMessage({ type: "OMERO_CONNECTED" });
      expect(await screen.findByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
    } finally {
      channel.close();
    }
  });
  test("A failed disconnect leaves the card connected.", async () => {
    const user = userEvent.setup();
    server.use(http.delete("/apps/omero/connect", () => new HttpResponse(null, { status: 500 })));

    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.present("MASKED") },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    await user.click(screen.getByRole("button", { name: "apps:actions.disconnect" }));

    // the UserConnection row still exists, so the card must not claim to be disconnected
    expect(await screen.findByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
    expect(screen.queryByRole("textbox", { name: "apps:integrations.omero.fields.username" })).not.toBeInTheDocument();
  });

  test("A failed connect broadcast does not mark the card as connected.", async () => {
    const user = userEvent.setup();
    const addAlert = vi.fn();
    render(
      <AlertContext.Provider value={{ addAlert, removeAlert: () => {} }}>
        <Omero
          integrationState={{
            mode: "DISABLED",
            credentials: { ACCESS_TOKEN: Optional.empty() },
          }}
          update={() => {}}
        />
      </AlertContext.Provider>,
    );

    await user.click(screen.getByRole("button"));
    const channel = new BroadcastChannel(OMERO_CONNECTION_CHANNEL);
    try {
      channel.postMessage({ type: "OMERO_CONNECTED", error: "bad credentials" });
      await waitFor(() =>
        expect(addAlert).toHaveBeenCalledWith(
          expect.objectContaining({ variant: "error", message: "bad credentials" }),
        ),
      );
    } finally {
      channel.close();
    }

    expect(screen.queryByRole("button", { name: "apps:actions.disconnect" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "apps:integrations.omero.fields.username" })).toBeVisible();
  });

  test("A successful connect clears the typed credentials.", async () => {
    const user = userEvent.setup();
    server.use(http.delete("/apps/omero/connect", () => new HttpResponse(null, { status: 200 })));
    render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: Optional.empty() },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    await user.type(screen.getByRole("textbox", { name: "apps:integrations.omero.fields.username" }), "omerouser");
    await user.type(screen.getByLabelText("apps:integrations.omero.fields.password"), "secret");

    const channel = new BroadcastChannel(OMERO_CONNECTION_CHANNEL);
    try {
      channel.postMessage({ type: "OMERO_CONNECTED" });
      expect(await screen.findByRole("button", { name: "apps:actions.disconnect" })).toBeVisible();
    } finally {
      channel.close();
    }

    // the password must not survive in component state, ready to be resubmitted after a disconnect
    await user.click(screen.getByRole("button", { name: "apps:actions.disconnect" }));
    expect(await screen.findByLabelText("apps:integrations.omero.fields.password")).toHaveValue("");
    expect(screen.getByRole("textbox", { name: "apps:integrations.omero.fields.username" })).toHaveValue("");
  });
});
