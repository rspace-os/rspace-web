import "@/__tests__/__mocks__/matchMedia";
import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { SidebarProvider, useSidebar } from "./sidebar";

function State() {
  const { state } = useSidebar();
  return <output aria-label="Sidebar state">{state}</output>;
}

test.each(["input", "textarea", "select", "editable"])("preserves shortcuts in %s", (kind) => {
  render(
    <SidebarProvider>
      <State />
      {kind === "input" ? (
        <input aria-label="Editor" />
      ) : kind === "textarea" ? (
        <textarea aria-label="Editor" />
      ) : kind === "select" ? (
        <select aria-label="Editor" />
      ) : (
        // biome-ignore lint/a11y/useSemanticElements: Exercises a rich text editing host.
        <div contentEditable suppressContentEditableWarning tabIndex={0} role="textbox" aria-label="Editor">
          <span>{"Editable child"}</span>
        </div>
      )}
    </SidebarProvider>,
  );
  const target = kind === "editable" ? screen.getByText("Editable child") : screen.getByLabelText("Editor");
  expect(fireEvent.keyDown(target, { key: "b", ctrlKey: true })).toBe(true);
  expect(screen.getByLabelText("Sidebar state")).toHaveTextContent("expanded");
});

test("can disable the shortcut without forwarding its prop to the wrapper", () => {
  render(
    <SidebarProvider keyboardShortcut={false} aria-label="Layout">
      <State />
    </SidebarProvider>,
  );
  expect(fireEvent.keyDown(window, { key: "b", metaKey: true })).toBe(true);
  expect(screen.getByLabelText("Sidebar state")).toHaveTextContent("expanded");
  expect(screen.getByLabelText("Layout")).not.toHaveAttribute("keyboardShortcut");
});

test.each(["ctrlKey", "metaKey"])("toggles by default with %s", (modifier) => {
  render(
    <SidebarProvider>
      <State />
    </SidebarProvider>,
  );
  expect(fireEvent.keyDown(window, { key: "b", [modifier]: true })).toBe(false);
  expect(screen.getByLabelText("Sidebar state")).toHaveTextContent("collapsed");
});

test.each([{ ctrlKey: true }, { metaKey: true }])("preserves a handled shortcut (%o)", (modifier) => {
  render(
    <SidebarProvider>
      <button type="button" onKeyDown={(event) => event.preventDefault()}>
        {"Editor command"}
      </button>
      <State />
    </SidebarProvider>,
  );
  fireEvent.keyDown(screen.getByRole("button", { name: "Editor command" }), {
    key: "b",
    ...modifier,
  });
  expect(screen.getByLabelText("Sidebar state")).toHaveTextContent("expanded");
});
