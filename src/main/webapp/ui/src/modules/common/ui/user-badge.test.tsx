import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { UserBadge, userInitials, userSwatch } from "./user-badge";

describe("UserBadge", () => {
  it("renders one compact identity from a full name and username", async () => {
    const { container } = render(<UserBadge name="Ada Lovelace" username="alovelace" />);

    expect(screen.getByText("Ada Lovelace (alovelace)")).toBeVisible();
    expect(screen.getByText("AL")).toBeInTheDocument();
    expect(screen.getByTitle("Ada Lovelace (alovelace)")).toHaveAttribute("data-slot", "user-badge");
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

    expect(screen.getByText("UU")).toHaveClass(...userSwatch("user user (user1a)").split(" "));
    expect(screen.getByText("user user (user1a)")).toBeVisible();
  });

  it("colours avatars by username so people with the same initials stay distinguishable", () => {
    render(
      <>
        <UserBadge name="user user" username="user" />
        <UserBadge name="user3 user3" username="user3" />
      </>,
    );

    const [first, second] = screen.getAllByText("UU");
    expect(first).toHaveClass(...userSwatch("user").split(" "));
    expect(second).toHaveClass(...userSwatch("user3").split(" "));
  });

  it("derives stable, distinct avatar colours", () => {
    expect(userSwatch("alovelace")).toBe(userSwatch("alovelace"));
    expect(userSwatch("user")).not.toBe(userSwatch("user3"));
  });

  it("falls back to the full name for the avatar colour when there is no username", () => {
    render(<UserBadge name="Grace Hopper" />);

    expect(screen.getByText("GH")).toHaveClass(...userSwatch("Grace Hopper").split(" "));
  });

  it("uses the first and last name initials without parsing usernames", () => {
    expect(userInitials("Maria van den Heuvel")).toBe("MH");
    expect(userInitials("researcher.01")).toBe("R");
    expect(userInitials(" ")).toBe("?");
  });
});
