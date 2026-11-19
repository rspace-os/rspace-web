import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import GroupEditBar from "../GroupEditBar";

const VALIDATION_MESSAGE = "Please enter some profile information.";

function renderEditBar(profileText: string) {
  const domContainer = document.createElement("div");
  domContainer.setAttribute("data-group-id", "1");
  domContainer.setAttribute("data-can-edit", "true");
  domContainer.setAttribute("data-profile-text", profileText);
  domContainer.setAttribute("data-profile-hiding-enabled", "true");
  domContainer.setAttribute("data-can-hide-group-profile", "true");
  domContainer.setAttribute("data-group-private-profile", "false");
  return render(<GroupEditBar domContainer={domContainer} />);
}

function mockEndpoints() {
  const profilePosts: Array<string> = [];
  const hidePosts: Array<string> = [];
  server.use(
    http.post("/groups/editProfile/1", async ({ request }) => {
      const newProfile = String((await request.formData()).get("newProfile"));
      profilePosts.push(newProfile);
      return newProfile.trim() === ""
        ? HttpResponse.json({ data: null, success: false, errorMsg: { errorMessages: [VALIDATION_MESSAGE] } })
        : HttpResponse.json({ data: true, success: true, errorMsg: null });
    }),
    http.post("/groups/ajax/admin/changeHideProfileSetting/1", async ({ request }) => {
      hidePosts.push(String((await request.formData()).get("hideProfile")));
      return HttpResponse.json({ data: "true", success: true });
    }),
  );
  return { profilePosts, hidePosts };
}

const editButton = () => screen.getByRole("button", { name: /editTooltip/ });
const saveButton = () => screen.getByRole("button", { name: /actions\.save/ });
const description = () => screen.getByRole("textbox", { name: /profileDescription/ });

describe("GroupEditBar", () => {
  it("shows the saved description as readable read-only text until editing starts", async () => {
    const user = userEvent.setup();
    const { baseElement } = renderEditBar("Saved description");

    expect(description()).toHaveValue("Saved description");
    expect(description()).toBeEnabled();
    expect(description()).toHaveAttribute("readonly");
    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible();

    await user.click(editButton());
    expect(description()).not.toHaveAttribute("readonly");
    await user.type(description(), "!");
    expect(description()).toHaveValue("Saved description!");
  });

  it("shows the no-profile wording outside edit mode and an empty field when editing", async () => {
    const user = userEvent.setup();
    renderEditBar("");

    expect(description()).toHaveDisplayValue(/noProfile/);
    expect(description()).toBeEnabled();

    await user.click(editButton());
    expect(description()).toHaveValue("");
  });

  it("hides a group whose empty description is unchanged without saving the description", async () => {
    const user = userEvent.setup();
    const { profilePosts, hidePosts } = mockEndpoints();
    renderEditBar("");

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());

    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());
    expect(hidePosts).toEqual(["true"]);
    expect(profilePosts).toEqual([]);
    expect(screen.queryByText(VALIDATION_MESSAGE)).not.toBeInTheDocument();
  });

  it("shows the backend validation message and keeps editing when the description is cleared", async () => {
    const user = userEvent.setup();
    const { profilePosts } = mockEndpoints();
    renderEditBar("Original");

    await user.click(editButton());
    await user.clear(description());
    await user.click(saveButton());

    expect(await screen.findByText(VALIDATION_MESSAGE)).toBeInTheDocument();
    expect(profilePosts).toEqual([""]);
    expect(saveButton()).toBeInTheDocument();
  });

  it("compares against the last saved description, not the initial one", async () => {
    const user = userEvent.setup();
    const { profilePosts } = mockEndpoints();
    renderEditBar("");

    await user.click(editButton());
    await user.type(description(), "abc");
    await user.click(saveButton());
    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());

    await user.click(editButton());
    await user.clear(description());
    await user.click(saveButton());

    expect(await screen.findByText(VALIDATION_MESSAGE)).toBeInTheDocument();
    expect(profilePosts).toEqual(["abc", ""]);
  });
});
