/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import ZenodoRepo from "../ZenodoRepo";
import "../../../../__mocks__/matchMedia";
import { type Person } from "../common";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();

  //eslint-disable-next-line
  global.fetch = () =>
    Promise.resolve({
      json: () => Promise.resolve([]),
    });
});

afterEach(cleanup);

describe("ZenodoRepo", () => {
  test("Upon editing, title should be set to the entered value.", async () => {
    const user = userEvent.setup();
    const handleChange = jest.fn<
      [
        { target: { name: "subject" | "title" | "description", value: string } }
      ],
      void
    >();

    const Wrapper = ({ onChange }: {| onChange: typeof handleChange |}) => {
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
          updatePeople={() => {}}
          title={title}
          description=""
          tags={[]}
          onTagsChange={() => {}}
          fetchingTags={false}
        />
      );
    };

    render(<Wrapper onChange={handleChange} />);

    await user.type(screen.getByRole("textbox", { name: /Title/ }), "foo");

    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "title",
        }),
      })
    );
  });

  test("Upon editing, description should be set to the entered value.", async () => {
    const user = userEvent.setup();
    const handleChange = jest.fn<
      [
        { target: { name: "subject" | "title" | "description", value: string } }
      ],
      void
    >();

    const Wrapper = ({ onChange }: {| onChange: typeof handleChange |}) => {
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
          updatePeople={() => {}}
          title=""
          description={description}
          tags={[]}
          onTagsChange={() => {}}
          fetchingTags={false}
        />
      );
    };

    render(<Wrapper onChange={handleChange} />);

    await user.type(
      screen.getByRole("textbox", { name: /Description/ }),
      "foo"
    );

    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "foo",
          name: "description",
        }),
      })
    );
  });

  test("Author and Contact should be set automatically to dummy values.", () => {
    const updatePeople = jest.fn<[Array<Person>], void>();

    render(
      <ZenodoRepo
        handleChange={() => {}}
        inputValidations={{
          description: true,
          title: true,
          author: true,
          contact: true,
          subject: true,
        }}
        submitAttempt={false}
        updatePeople={updatePeople}
        title=""
        description=""
        tags={[]}
        onTagsChange={() => {}}
        fetchingTags={false}
      />
    );

    expect(updatePeople).toHaveBeenLastCalledWith(
      expect.arrayContaining([
        {
          uniqueName: "DUMMY_VALUE",
          email: "DUMMY_VALUE@example.com",
          type: "Author",
        },
        {
          uniqueName: "DUMMY_VALUE",
          email: "DUMMY_VALUE@example.com",
          type: "Contact",
        },
      ])
    );
  });

  test("Subject should be set automatically to a dummy value.", () => {
    const handleChange = jest.fn<
      [
        { target: { name: "subject" | "title" | "description", value: string } }
      ],
      void
    >();

    render(
      <ZenodoRepo
        handleChange={handleChange}
        inputValidations={{
          description: true,
          title: true,
          author: true,
          contact: true,
          subject: true,
        }}
        submitAttempt={false}
        updatePeople={() => {}}
        title=""
        description=""
        tags={[]}
        onTagsChange={() => {}}
        fetchingTags={false}
      />
    );

    expect(handleChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        target: expect.objectContaining({
          value: "DUMMY_VALUE",
          name: "subject",
        }),
      })
    );
  });
});
