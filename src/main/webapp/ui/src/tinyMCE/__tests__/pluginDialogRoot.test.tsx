// biome-ignore-all lint/style/noJsxLiterals: Test fixture labels are not application text.
import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createPluginDialog, type PluginDialog } from "../pluginDialogRoot";

vi.mock("react-dom/client", { spy: true });

function StatefulChild({ open = true, onCleanup }: { open?: boolean; onCleanup: () => void }) {
  const [count, setCount] = useState(0);
  useEffect(() => onCleanup, [onCleanup]);
  return (
    <button type="button" hidden={!open} onClick={() => setCount(count + 1)}>
      Count {count}
    </button>
  );
}

let nextContainer = 0;
const handles: PluginDialog[] = [];
const containers: HTMLElement[] = [];

function createHandles() {
  const id = `plugin-dialog-test-${nextContainer++}`;
  const a = createPluginDialog(id);
  const b = createPluginDialog(id);
  handles.push(a, b);
  return { id, a, b };
}

function getContainer(id: string) {
  const container = document.getElementById(id);
  if (!container) throw new Error(`Missing dialog container ${id}`);
  containers.push(container);
  return container;
}

afterEach(() => {
  act(() => {
    handles.forEach((handle) => {
      handle.release();
    });
  });
  containers.forEach((container) => {
    container.remove();
  });
  handles.length = 0;
  containers.length = 0;
  vi.clearAllMocks();
});

describe("createPluginDialog", () => {
  it("creates one container and root for two handles only when opened", () => {
    const { id, a, b } = createHandles();
    act(() => {
      a.update(<p>Ignored</p>);
      a.release();
    });
    expect(createRoot).not.toHaveBeenCalled();
    expect(document.getElementById(id)).toBeNull();

    act(() => a.open(<p>Editor A</p>));
    const container = getContainer(id);
    act(() => b.open(<p>Editor B</p>));

    expect(getContainer(id)).toBe(container);
    expect(createRoot).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Editor B")).toBeInTheDocument();
  });

  it("preserves state through same-owner updates, close and reopen", async () => {
    const user = userEvent.setup();
    const { id, a } = createHandles();
    const onCleanup = vi.fn();
    act(() => a.open(<StatefulChild onCleanup={onCleanup} />));
    getContainer(id);
    await user.click(screen.getByRole("button", { name: "Count 0" }));

    act(() => a.update(<StatefulChild onCleanup={onCleanup} />));
    expect(screen.getByRole("button", { name: "Count 1" })).toBeInTheDocument();
    act(() => a.update(<StatefulChild open={false} onCleanup={onCleanup} />));
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    act(() => a.open(<StatefulChild onCleanup={onCleanup} />));

    expect(screen.getByRole("button", { name: "Count 1" })).toBeInTheDocument();
    expect(onCleanup).not.toHaveBeenCalled();
  });

  it("cleans up the previous owner's tree and starts with fresh state", async () => {
    const user = userEvent.setup();
    const { id, a, b } = createHandles();
    const onCleanup = vi.fn();
    act(() => a.open(<StatefulChild onCleanup={onCleanup} />));
    getContainer(id);
    await user.click(screen.getByRole("button", { name: "Count 0" }));

    act(() => b.open(<StatefulChild onCleanup={onCleanup} />));

    expect(screen.getByRole("button", { name: "Count 0" })).toBeInTheDocument();
    expect(onCleanup).toHaveBeenCalledTimes(1);
  });

  it("ignores updates and release from a previous owner, and clears the owner's tree on release", () => {
    const { id, a, b } = createHandles();
    act(() => a.open(<p>Editor A</p>));
    getContainer(id);
    act(() => b.open(<p>Editor B</p>));
    act(() => a.update(<p>Stale update</p>));
    expect(screen.getByText("Editor B")).toBeInTheDocument();
    act(() => a.release());
    expect(screen.getByText("Editor B")).toBeInTheDocument();

    act(() => b.release());
    expect(screen.queryByText("Editor B")).not.toBeInTheDocument();
    act(() => b.update(<p>Released update</p>));
    expect(screen.queryByText("Released update")).not.toBeInTheDocument();
  });

  it("recovers a detached container only on open and resolves the replacement root for existing handles", () => {
    const { id, a, b } = createHandles();
    const onCleanup = vi.fn();
    act(() => a.open(<StatefulChild onCleanup={onCleanup} />));
    const oldContainer = getContainer(id);
    oldContainer.remove();
    act(() => b.update(<p>Ignored</p>));
    expect(createRoot).toHaveBeenCalledTimes(1);
    expect(document.getElementById(id)).toBeNull();

    act(() => b.open(<p>Editor B</p>));
    expect(getContainer(id)).not.toBe(oldContainer);
    expect(getContainer(id).isConnected).toBe(true);
    expect(onCleanup).toHaveBeenCalledTimes(1);
    expect(createRoot).toHaveBeenCalledTimes(2);
    act(() => a.update(<p>Stale update</p>));
    expect(screen.getByText("Editor B")).toBeInTheDocument();
    act(() => a.open(<p>Editor A returns</p>));
    expect(screen.getByText("Editor A returns")).toBeInTheDocument();
    expect(createRoot).toHaveBeenCalledTimes(2);
  });
});
