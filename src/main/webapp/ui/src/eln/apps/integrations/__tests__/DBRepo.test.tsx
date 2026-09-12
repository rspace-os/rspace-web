import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { Optional } from "@/util/optional";
import DBRepo from "../DBRepo";

import "@/__tests__/__mocks__/matchMedia";

describe("DBRepo", () => {
  test("renders endpoint, username, and password fields.", async () => {
    const user = userEvent.setup();
    render(
      <DBRepo
        integrationState={{
          mode: "DISABLED",
          credentials: {
            DBREPO_URL: Optional.present("http://localhost:8080"),
            DBREPO_CONNECTED: false,
            optionsId: Optional.present("1"),
          },
        }}
        update={() => {}}
      />,
    );

    await user.click(screen.getByRole("button"));

    expect(screen.getByRole("textbox", { name: "apps:integrations.dbrepo.fields.url" })).toHaveValue(
      "http://localhost:8080",
    );
    expect(screen.getByRole("textbox", { name: "apps:integrations.dbrepo.fields.username" })).toBeVisible();
    expect(screen.getByLabelText("apps:integrations.dbrepo.fields.password")).toBeVisible();
    expect(screen.getByText("apps:integrations.dbrepo.alerts.httpWarning")).toBeVisible();
  });

  test("connect form posts to DBRepo controller without opening a blank tab first.", async () => {
    const user = userEvent.setup();
    const open = vi.spyOn(window, "open").mockReturnValue(null);
    try {
      render(
        <DBRepo
          integrationState={{
            mode: "DISABLED",
            credentials: {
              DBREPO_URL: Optional.empty(),
              DBREPO_CONNECTED: false,
              optionsId: Optional.empty(),
            },
          }}
          update={() => {}}
        />,
      );

      await user.click(screen.getByRole("button"));
      await user.type(
        screen.getByRole("textbox", { name: "apps:integrations.dbrepo.fields.url" }),
        "https://dbrepo.example",
      );
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.dbrepo.fields.username" }), "user");
      await user.type(screen.getByLabelText("apps:integrations.dbrepo.fields.password"), "password");
      const form = screen.getByRole("form", { name: "apps:integrations.dbrepo.credentialsFormLabel" });
      const submit = vi.fn((event: Event) => event.preventDefault());
      form.addEventListener("submit", submit);
      await user.click(screen.getByRole("button", { name: "apps:actions.connect" }));

      expect(form).toHaveAttribute("action", "/apps/dbrepo/connect");
      expect(form).toHaveAttribute("method", "POST");
      expect(form).toHaveAttribute("target", "_blank");
      expect(submit).toHaveBeenCalledOnce();
      expect(open).not.toHaveBeenCalled();
    } finally {
      open.mockRestore();
    }
  });
});
