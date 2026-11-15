import { render, screen } from "@testing-library/react";
import React from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import ZenodoRepo from "../ZenodoRepo";
import "@/__tests__/__mocks__/matchMedia";

import userEvent from "@testing-library/user-event";

beforeEach(() => {
  vi.clearAllMocks();
  global.fetch = () =>
    Promise.resolve({
      json: () => Promise.resolve([]),
    } as Response);
});
describe("ZenodoRepo", () => {
  test("Upon editing, title should be set to the entered value.", async () => {
    const user = userEvent.setup();

    const handleChange = vi.fn();
    const Wrapper = ({ onChange }: { onChange: typeof handleChange }) => {
      const [title, setTitle] = React.useState("");
      return (
        <ZenodoRepo
          handleChange={(e) => {
            if (e.target.name === "title") setTitle(e.target.value);
            onChange(e);
          }}
          inputValidations={{
            description: true,
            title: true,
            author: true,
            contact: true,
            subject: true,
          }}
          submitAttempt={false}
          title={title}
          description=""
          tags={[]}
          onTagsChange={() => {}}
          fetchingTags={false}
        />
      );
    };

    render(<Wrapper onChange={handleChange} />);

    await user.type(screen.getByRole("textbox", { name: "workspace:export.repositories.common.title" }), "foo");
    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "title",
        }),
      }),
    );
  });
  test("Upon editing, description should be set to the entered value.", async () => {
    const user = userEvent.setup();

    const handleChange = vi.fn();
    const Wrapper = ({ onChange }: { onChange: typeof handleChange }) => {
      const [description, setDescription] = React.useState("");
      return (
        <ZenodoRepo
          handleChange={(e) => {
            if (e.target.name === "description") setDescription(e.target.value);
            onChange(e);
          }}
          inputValidations={{
            description: true,
            title: true,
            author: true,
            contact: true,
            subject: true,
          }}
          submitAttempt={false}
          title=""
          description={description}
          tags={[]}
          onTagsChange={() => {}}
          fetchingTags={false}
        />
      );
    };

    render(<Wrapper onChange={handleChange} />);
    await user.type(screen.getByRole("textbox", { name: "workspace:export.repositories.common.description" }), "foo");
    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "description",
        }),
      }),
    );
  });
});
