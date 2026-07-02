import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import Omero from "../Omero";

import "@/__tests__/__mocks__/matchMedia";

describe("Omero", () => {
  test("Should have no axe violations.", async () => {
    const user = userEvent.setup();
    const { baseElement } = render(
      <Omero
        integrationState={{
          mode: "DISABLED",
          credentials: {},
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
          credentials: {},
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("textbox", { name: "Username" })).toBeVisible();
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    expect(screen.getByLabelText("Password")).toBeVisible();
  });
  test("Connect form should submit without opening a blank tab first.", async () => {
    const user = userEvent.setup();
    const open = vi.spyOn(window, "open").mockReturnValue(null);
    try {
      render(
        <Omero
          integrationState={{
            mode: "DISABLED",
            credentials: {},
          }}
          update={() => {}}
        />,
      );

      await user.click(screen.getByRole("button"));
      await user.type(screen.getByRole("textbox", { name: "Username" }), "user");
      await user.type(screen.getByLabelText("Password"), "password");
      const form = screen.getByRole("form", { name: "OMERO credentials" });
      const submit = vi.fn((event: Event) => event.preventDefault());
      form.addEventListener("submit", submit);
      await user.click(screen.getByRole("button", { name: "Connect" }));

      expect(form).toHaveAttribute("action", "/apps/omero/connect");
      expect(form).toHaveAttribute("method", "POST");
      expect(form).toHaveAttribute("target", "_blank");
      expect(submit).toHaveBeenCalledOnce();
      expect(open).not.toHaveBeenCalled();
    } finally {
      open.mockRestore();
    }
  });
});
