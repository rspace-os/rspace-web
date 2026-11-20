import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { createContext, runInContext } from "node:vm";
import { describe, expect, it } from "vitest";

type DragOverEvent = {
  target: unknown;
  preventDefault: () => void;
  stopPropagation: () => void;
  originalEvent: { defaultPrevented: boolean; dataTransfer: { dropEffect: string } };
};

function loadDragOverHandler(): (event: DragOverEvent) => void {
  const document = {};
  const handlers: Record<string, (event: DragOverEvent) => void> = {};
  const jquery = (value: unknown) => {
    if (value === document) {
      return {
        on: (eventName: string, handler: (event: DragOverEvent) => void) => {
          handlers[eventName] = handler;
        },
      };
    }
    return { is: () => false, parent: () => ({ is: () => false }) };
  };

  const context = createContext({ $: jquery, document });
  const script = readFileSync(resolve(__dirname, "../../../scripts/pages/gallery/galleryFileUpload.js"), "utf8");
  runInContext(script, context);
  runInContext(
    `markAreaDroppable = function () {};
     _resetAreaCleanupTimeout = function () {};
     initDragDropAreaHandling(".field", function () {}, function () {});`,
    context,
  );

  const dragover = handlers.dragover;
  if (!dragover) throw new Error("dragover handler was not registered");
  return dragover;
}

function dragOverEvent(defaultPrevented: boolean): DragOverEvent {
  return {
    target: {},
    preventDefault: () => {},
    stopPropagation: () => {},
    originalEvent: { defaultPrevented, dataTransfer: { dropEffect: "copy" } },
  };
}

describe("galleryFileUpload document dragover handler", () => {
  it("keeps the dropEffect of an inner drop target that accepted the drag", () => {
    const event = dragOverEvent(true);
    loadDragOverHandler()(event);
    expect(event.originalEvent.dataTransfer.dropEffect).toBe("copy");
  });

  it("refuses drops outside the legacy drop area when nothing accepted the drag", () => {
    const event = dragOverEvent(false);
    loadDragOverHandler()(event);
    expect(event.originalEvent.dataTransfer.dropEffect).toBe("none");
  });
});
