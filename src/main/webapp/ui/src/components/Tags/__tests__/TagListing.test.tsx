import "@/__tests__/__mocks__/muiTransitions";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { Optional } from "../../../util/optional";
import TagListing from "../TagListing";

const ontologyTag = {
  value: "Shared label",
  uri: Optional.present("https://example.org/a"),
  vocabulary: Optional.present("Vocabulary A"),
  version: Optional.present("1.0"),
};
const plainTag = {
  value: "Plain label",
  uri: Optional.empty<string>(),
  vocabulary: Optional.empty<string>(),
  version: Optional.empty<string>(),
};

const searchQuery = (link: HTMLElement) =>
  new URL(link.getAttribute("href") ?? "", "http://localhost").searchParams.get("query");

afterEach(() => {
  cleanup();
});

describe("TagListing", () => {
  test("clicking an ontology tag opens its metadata with a search link", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<TagListing tags={[ontologyTag]} onClick={onClick} />);

    await user.click(screen.getByRole("button", { name: "Shared label" }));

    expect(screen.getByText("https://example.org/a")).toBeVisible();
    const search = screen.getByRole("link", { name: "common:tags.metadata.search" });
    expect(searchQuery(search)).toBe('l: (tags:"Shared label")');
    await user.click(search);
    expect(onClick).toHaveBeenCalledWith(ontologyTag);
  });

  test("Enter on an ontology tag opens its metadata", async () => {
    const user = userEvent.setup();
    render(<TagListing tags={[ontologyTag]} />);

    screen.getByRole("button", { name: "Shared label" }).focus();
    await user.keyboard("{Enter}");

    expect(screen.getByText("https://example.org/a")).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  test("clicking the icon of an ontology tag opens its metadata, not the enclosing row", async () => {
    const user = userEvent.setup();
    const onRowClick = vi.fn();
    const { container } = render(
      // biome-ignore lint/a11y/noStaticElementInteractions: stands in for a clickable table row
      // biome-ignore lint/a11y/useKeyWithClickEvents: stands in for a clickable table row
      <div onClick={onRowClick}>
        <TagListing tags={[ontologyTag]} onClick={vi.fn()} />
      </div>,
    );

    await user.click(container.querySelector("svg") as SVGElement);

    expect(screen.getByText("https://example.org/a")).toBeVisible();
    expect(onRowClick).not.toHaveBeenCalled();
  });

  test("search links keep labels with URL syntax whole", () => {
    render(
      <TagListing
        tags={[
          { ...plainTag, value: "Salt & pepper" },
          { ...plainTag, value: "Batch #2" },
        ]}
        onClick={vi.fn()}
      />,
    );

    expect(searchQuery(screen.getByRole("link", { name: "Salt & pepper" }))).toBe('l: (tags:"Salt & pepper")');
    expect(searchQuery(screen.getByRole("link", { name: "Batch #2" }))).toBe('l: (tags:"Batch #2")');
  });

  test("a plain tag still searches directly", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<TagListing tags={[plainTag]} onClick={onClick} />);

    await user.click(screen.getByRole("link", { name: "Plain label" }));

    expect(onClick).toHaveBeenCalledWith(plainTag);
  });

  test("a deletable ontology tag has separate metadata and delete buttons", async () => {
    const user = userEvent.setup();
    const onDelete = vi.fn();
    const { baseElement } = render(<TagListing tags={[ontologyTag]} onDelete={onDelete} onClick={vi.fn()} />);
    const open = screen.getByRole("button", { name: "Shared label" });
    const remove = screen.getByRole("button", { name: "common:tags.removeTag" });

    expect(open).not.toContainElement(remove);
    expect(remove).not.toContainElement(open);
    await expectAccessible(baseElement);

    await user.tab();
    expect(open).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(screen.getByText("https://example.org/a")).toBeVisible();
    // Searching would leave the form and drop its unsaved changes.
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(onDelete).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    expect(open).toHaveFocus();

    await user.tab();
    expect(remove).toHaveFocus();
    await user.keyboard("{Enter}");
    expect(onDelete).toHaveBeenCalledWith(0, ontologyTag);
  });
});
