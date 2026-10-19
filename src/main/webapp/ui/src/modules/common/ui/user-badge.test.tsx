import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { UserBadge, userInitials } from "./user-badge";

describe("UserBadge", () => {
  it("renders one compact identity from a full name and username", async () => {
    const { container } = render(<UserBadge name="Ada Lovelace" username="alovelace" />);

    expect(screen.getByText("Ada Lovelace (alovelace)")).toBeVisible();
    expect(screen.getByText("AL")).toBeInTheDocument();
    await expectAccessible(container);
  });

  it("does not duplicate a username already present in the display name", () => {
    render(<UserBadge name="Grace Hopper (ghopper)" username="ghopper" density="compact" />);

    expect(screen.getByText("Grace Hopper (ghopper)")).toBeVisible();
    expect(screen.getByText("GH")).toBeInTheDocument();
    expect(screen.queryByText("Grace Hopper (ghopper) (ghopper)")).not.toBeInTheDocument();
  });

  it("uses name initials for a combined API display name without a separate username", () => {
    render(<UserBadge name="user user (user1a)" />);

    expect(screen.getByText("UU")).toBeInTheDocument();
    expect(screen.getByText("user user (user1a)")).toBeVisible();
  });

  it("uses the first and last name initials without parsing usernames", () => {
    expect(userInitials("Maria van den Heuvel")).toBe("MH");
    expect(userInitials("researcher.01")).toBe("R");
    expect(userInitials(" ")).toBe("?");
  });
});
