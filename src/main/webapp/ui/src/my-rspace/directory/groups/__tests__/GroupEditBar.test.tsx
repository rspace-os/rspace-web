import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import GroupEditBar from "../GroupEditBar";

const VALIDATION_MESSAGE = "Please enter some profile information.";

function renderEditBar(profileText: string, { piCanEditAll = false }: { piCanEditAll?: boolean } = {}) {
  const domContainer = document.createElement("div");
  if (piCanEditAll) {
    domContainer.setAttribute("data-can-editpieditallchioce", "true");
    domContainer.setAttribute("data-pieditallchoicevalue", "false");
  }
  domContainer.setAttribute("data-group-id", "1");
  domContainer.setAttribute("data-can-edit", "true");
  domContainer.setAttribute("data-profile-text", profileText);
  domContainer.setAttribute("data-profile-hiding-enabled", "true");
  domContainer.setAttribute("data-can-hide-group-profile", "true");
  domContainer.setAttribute("data-group-private-profile", "false");
  return render(<GroupEditBar domContainer={domContainer} />);
}

function mockEndpoints({
  hideStatus = 200,
  hideResponseGate,
}: {
  hideStatus?: number;
  hideResponseGate?: Promise<void>;
} = {}) {
  const profilePosts: Array<string> = [];
  const hidePosts: Array<string> = [];
  const piCanEditPosts: Array<string> = [];
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
      await hideResponseGate;
      return hideStatus === 200
        ? HttpResponse.json({ data: "true", success: true })
        : HttpResponse.json({ exceptionMessage: "Server error" }, { status: hideStatus });
    }),
    http.post("/groups/ajax/admin/changePiCanEditAll/1", async ({ request }) => {
      piCanEditPosts.push(String((await request.formData()).get("canPIEditAll")));
      return HttpResponse.json({ data: "true", success: true });
    }),
  );
  return { profilePosts, hidePosts, piCanEditPosts };
}

const editButton = () => screen.getByRole("button", { name: /editTooltip/ });
const saveButton = () => screen.getByRole("button", { name: /actions\.save/ });
const description = () => screen.getByRole("textbox", { name: /profileDescription/ });

describe("GroupEditBar", () => {
  it("shows the saved description as plain text, with no textbox, until editing starts", async () => {
    const user = userEvent.setup();
    const { baseElement } = renderEditBar("Saved description");

    expect(screen.getByText("Saved description")).toBeVisible();
    expect(screen.getByText(/profileDescription/)).toBeVisible();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    await expectAccessible(baseElement);

    await user.click(editButton());
    expect(description()).toHaveValue("Saved description");
    await user.type(description(), "!");
    expect(description()).toHaveValue("Saved description!");
  });

  it("shows the no-profile wording outside edit mode and an empty field when editing", async () => {
    const user = userEvent.setup();
    renderEditBar("");

    expect(screen.getByText(/noProfile/)).toBeVisible();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

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

  it("compares the hide setting against the last saved value, not the initial one", async () => {
    const user = userEvent.setup();
    const { hidePosts } = mockEndpoints();
    renderEditBar("Original");

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());
    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());

    await waitFor(() => expect(hidePosts).toEqual(["true", "false"]));
  });

  it("compares the PI can-edit-all setting against the last saved value", async () => {
    const user = userEvent.setup();
    const { piCanEditPosts } = mockEndpoints();
    renderEditBar("Original", { piCanEditAll: true });

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /piCanEdit/ }));
    await user.click(saveButton());
    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /piCanEdit/ }));
    await user.click(saveButton());

    await waitFor(() => expect(piCanEditPosts).toEqual(["true", "false"]));
  });

  it("sends the hide setting again on retry after it was applied in a partly failed save", async () => {
    const user = userEvent.setup();
    const { profilePosts, hidePosts } = mockEndpoints();
    renderEditBar("Original");

    await user.click(editButton());
    await user.clear(description());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());
    expect(await screen.findByText(VALIDATION_MESSAGE)).toBeInTheDocument();
    expect(hidePosts).toEqual(["true"]);
    expect(saveButton()).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.type(description(), "Fixed");
    await user.click(saveButton());

    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());
    expect(hidePosts).toEqual(["true", "false"]);
    expect(profilePosts).toEqual(["", "Fixed"]);
  });

  it("restores the saved description when a rejected edit is cancelled", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderEditBar("Original");

    await user.click(editButton());
    await user.clear(description());
    await user.click(saveButton());
    expect(await screen.findByText(VALIDATION_MESSAGE)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /actions\.cancel/ }));
    expect(screen.getByText("Original")).toBeVisible();

    await user.click(editButton());
    expect(description()).toHaveValue("Original");
  });

  it("restores the saved hide setting when an unsaved change is cancelled", async () => {
    const user = userEvent.setup();
    const { hidePosts } = mockEndpoints();
    renderEditBar("Original");

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(screen.getByRole("button", { name: /actions\.cancel/ }));

    await user.click(editButton());
    expect(screen.getByRole("checkbox", { name: /hideGroup/ })).not.toBeChecked();
    await user.click(saveButton());
    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());
    expect(hidePosts).toEqual([]);
  });

  it("disables Save and Cancel while a save is in flight, then shows what was saved", async () => {
    const user = userEvent.setup();
    let releaseHide = () => {};
    const hideResponseGate = new Promise<void>((resolve) => {
      releaseHide = resolve;
    });
    const { hidePosts } = mockEndpoints({ hideResponseGate });
    renderEditBar("Original", { piCanEditAll: true });

    await user.click(editButton());
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());

    await waitFor(() => expect(hidePosts).toEqual(["true"]));
    expect(screen.getByRole("button", { name: /actions\.cancel/ })).toBeDisabled();
    expect(saveButton()).toBeDisabled();
    expect(description()).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /hideGroup/ })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /piCanEdit/ })).toBeDisabled();

    releaseHide();
    await waitFor(() => expect(screen.queryByRole("button", { name: /actions\.save/ })).not.toBeInTheDocument());
    await user.click(editButton());
    expect(screen.getByRole("checkbox", { name: /hideGroup/ })).toBeChecked();
  });

  it("reports a failed hide request, keeps editing, and still saves the changed description", async () => {
    const user = userEvent.setup();
    const { profilePosts, hidePosts } = mockEndpoints({ hideStatus: 500 });
    renderEditBar("Original");

    await user.click(editButton());
    await user.type(description(), " text");
    await user.click(screen.getByRole("checkbox", { name: /hideGroup/ }));
    await user.click(saveButton());

    expect(await screen.findByText(/editBar\.errorMessage/)).toBeInTheDocument();
    expect(hidePosts).toEqual(["true"]);
    expect(profilePosts).toEqual(["Original text"]);
    expect(saveButton()).toBeInTheDocument();
  });
});
