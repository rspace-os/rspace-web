import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import UserSelect from "../UserSelect";

describe("UserSelect", () => {
  beforeEach(() => {
    server.use(
      http.get("/workspace/ajax/getViewablePublicUserInfoList", () =>
        HttpResponse.json({ data: [{ username: "user3c", firstName: "Ada", lastName: "Lovelace" }] }),
      ),
    );
  });

  test("a username the viewer cannot see stays selected, shown by its username", async () => {
    const updateSelected = vi.fn();
    render(<UserSelect selected="nosuchuser" updateSelected={updateSelected} />);

    expect(await screen.findByText("nosuchuser")).toBeVisible();
    expect(updateSelected).toHaveBeenCalledWith([{ username: "nosuchuser" }], "username");
    expect(screen.getByRole("combobox")).toBeVisible();
  });

  test("usernames the viewer can and cannot see are all kept, in order", async () => {
    const updateSelected = vi.fn();
    render(<UserSelect selected="user3c<<>>nosuchuser" updateSelected={updateSelected} />);

    expect(await screen.findByText("Ada Lovelace - user3c")).toBeVisible();
    expect(screen.getByText("nosuchuser")).toBeVisible();
    expect(updateSelected).toHaveBeenCalledWith(
      [expect.objectContaining({ username: "user3c" }), { username: "nosuchuser" }],
      "username",
    );
  });
});
