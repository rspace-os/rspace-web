import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import CreateGroupStep3 from "../CreateGroupStep3";

afterEach(() => vi.unstubAllGlobals());

test("keeps invitations unique across repeated entry, case variants and both panels", async () => {
  const user = userEvent.setup();
  const updateInvitedMembers = vi.fn();
  vi.stubGlobal("$", (selector: string) => document.querySelectorAll(selector));
  // The legacy chip list has no React keys.
  const restoreConsole = silenceConsole(["error"], ['Each child in a list should have a unique "key" prop']);
  try {
    render(
      <CreateGroupStep3
        existingUsers={["member@example.com"]}
        newUsers={[]}
        updateInvitedMembers={updateInvitedMembers}
      />,
    );
    const input = screen.getByRole("textbox");
    await user.type(input, "invite@example.com{Enter}");
    await user.type(input, "invite@example.com{Enter}");
    await user.type(input, "INVITE@example.com{Enter}");
    await user.type(input, "member@example.com{Enter}");

    expect(input).toHaveValue("");
    expect(screen.getAllByRole("button", { name: "invite@example.com" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "member@example.com" })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "INVITE@example.com" })).not.toBeInTheDocument();
    expect(updateInvitedMembers).toHaveBeenLastCalledWith(["member@example.com"], ["invite@example.com"]);
  } finally {
    restoreConsole();
  }
});
