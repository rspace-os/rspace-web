import { type Locator, page, userEvent } from "vitest/browser";

type Point = { x: number; y: number };

function center(element: Element): Point {
  const rect = element.getBoundingClientRect();
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

function itemFor(handle: Locator): HTMLElement {
  const item = handle.element().closest<HTMLElement>('li, [data-slot="badge"]');
  if (!item) throw new Error(`No sortable item contains ${handle.toString()}`);
  return item;
}

function mouseEvent(type: string, point: Point): MouseEvent {
  return new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    button: 0,
    buttons: type === "mouseup" ? 0 : 1,
    clientX: point.x,
    clientY: point.y,
  });
}

function touch(target: EventTarget, point: Point): Touch {
  const init: TouchInit = {
    identifier: 1,
    target,
    clientX: point.x,
    clientY: point.y,
    screenX: point.x,
    screenY: point.y,
    pageX: point.x,
    pageY: point.y,
    radiusX: 1,
    radiusY: 1,
    rotationAngle: 0,
    force: 1,
  };
  return init as Touch;
}

function touchEvent(type: string, target: EventTarget, point: Point): TouchEvent {
  const contact = touch(target, point);
  const ending = type === "touchend" || type === "touchcancel";
  let TouchEventConstructor = window.TouchEvent;
  if (!TouchEventConstructor) {
    TouchEventConstructor = class extends Event {} as unknown as typeof TouchEvent;
    Object.defineProperty(window, "TouchEvent", { configurable: true, value: TouchEventConstructor });
  }
  let event: TouchEvent;
  try {
    event = new TouchEventConstructor(type, { bubbles: true, cancelable: true });
  } catch {
    TouchEventConstructor = class extends Event {} as unknown as typeof TouchEvent;
    Object.defineProperty(window, "TouchEvent", { configurable: true, value: TouchEventConstructor });
    event = new TouchEventConstructor(type, { bubbles: true, cancelable: true });
  }
  Object.defineProperties(event, {
    touches: { value: ending ? [] : [contact] },
    targetTouches: { value: ending ? [] : [contact] },
    changedTouches: { value: [contact] },
  });
  return event;
}

async function nextFrame(): Promise<void> {
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
}

export class TableListReorderingPage {
  private mousePosition: Point | null = null;
  private resizePosition: Point | null = null;
  private touchPosition: Point | null = null;
  private touchTarget: EventTarget | null = null;

  get filterButton(): Locator {
    return page.getByRole("button", { name: "Filters, 12 applied" });
  }

  get sortingButton(): Locator {
    return page.getByRole("button", { name: "Sort, 3 rules active" });
  }

  get columnsButton(): Locator {
    return page.getByRole("button", { name: /^Columns/ });
  }

  get filterList(): Locator {
    return page.getByRole("list", { name: "Filter records" });
  }

  get sortingList(): Locator {
    return page.getByRole("list", { name: "Sort priority" });
  }

  get shownColumns(): Locator {
    return page.getByRole("group", { name: "Shown" });
  }

  get hiddenColumns(): Locator {
    return page.getByRole("group", { name: "Hidden" });
  }

  get filterState(): Locator {
    return page.getByLabelText("Filter state");
  }

  get sortingState(): Locator {
    return page.getByLabelText("Sorting state");
  }

  get columnState(): Locator {
    return page.getByLabelText("Column state");
  }

  get commitCounts(): Locator {
    return page.getByLabelText("Commit counts");
  }

  filterHandle(number: number): Locator {
    return page.getByRole("button", { name: `Drag filter ${number}`, exact: true });
  }

  sortingHandle(number: number): Locator {
    return page.getByRole("button", { name: `Drag sort rule ${number}` });
  }

  columnHandle(label: string): Locator {
    return page.getByRole("button", { name: `Drag ${label} column` });
  }

  shownColumnHandle(label: string): Locator {
    return this.shownColumns.getByRole("button", { name: `Drag ${label} column` });
  }

  hiddenColumnHandle(label: string): Locator {
    return this.hiddenColumns.getByRole("button", { name: `Drag ${label} column` });
  }

  sortingDirection(label: string): Locator {
    return page.getByRole("combobox", { name: `Direction for ${label}` });
  }

  removeSort(label: string): Locator {
    return page.getByRole("button", { name: `Remove ${label} from sorting` });
  }

  resizeHandle(label: string): Locator {
    return page.getByRole("button", { name: `Resize column ${label}` });
  }

  async openFilters(): Promise<void> {
    await userEvent.click(this.filterButton);
    await this.filterList.findElement();
  }

  async openSorting(): Promise<void> {
    await userEvent.click(this.sortingButton);
    await this.sortingList.findElement();
  }

  async openColumns(): Promise<void> {
    await userEvent.click(this.columnsButton);
    await this.shownColumns.findElement();
  }

  async resetCommitCounts(): Promise<void> {
    await userEvent.click(page.getByRole("button", { name: "Reset change counters" }));
  }

  async applyFilters(): Promise<void> {
    await userEvent.click(page.getByRole("button", { name: "Apply filters" }));
  }

  async selectSortingDirection(label: string, direction: "asc" | "desc"): Promise<void> {
    await userEvent.selectOptions(this.sortingDirection(label), direction);
  }

  async removeSorting(label: string): Promise<void> {
    await userEvent.click(this.removeSort(label));
  }

  text(locator: Locator): string {
    return locator.element().textContent ?? "";
  }

  itemTop(handle: Locator): number {
    return itemFor(handle).getBoundingClientRect().top;
  }

  itemLeft(handle: Locator): number {
    return itemFor(handle).getBoundingClientRect().left;
  }

  itemWidth(handle: Locator): number {
    return itemFor(handle).getBoundingClientRect().width;
  }

  isDragging(handle: Locator): boolean {
    return itemFor(handle).classList.contains("opacity-60");
  }

  columnDragOverlayText(): string {
    return document.querySelector<HTMLElement>('[data-slot="badge"][aria-hidden="true"]')?.textContent?.trim() ?? "";
  }

  filterScrollTop(): number {
    return this.filterList.element().scrollTop;
  }

  filterDraftOrder(): string {
    return Array.from(this.filterList.element().querySelectorAll<HTMLInputElement>('input[type="text"]'))
      .map((input) => input.value)
      .join(",");
  }

  columnWidth(label: string): number {
    const header = this.resizeHandle(label).element().closest("th");
    if (!header) throw new Error(`No column header contains the ${label} resize handle`);
    return header.getBoundingClientRect().width;
  }

  resizeIndicatorTransform(label: string): string {
    const indicator = this.resizeHandle(label).element().querySelector<HTMLElement>('span[aria-hidden="true"]');
    if (!indicator) throw new Error(`No resize indicator found for ${label}`);
    return indicator.style.transform;
  }

  async startMouseColumnResize(label: string): Promise<void> {
    const handle = this.resizeHandle(label).element();
    const start = center(handle);
    handle.dispatchEvent(mouseEvent("mousedown", start));
    this.resizePosition = start;
    await nextFrame();
  }

  async moveMouseColumnResize(delta: number): Promise<void> {
    if (!this.resizePosition) throw new Error("No column resize is active");
    this.resizePosition = { x: this.resizePosition.x + delta, y: this.resizePosition.y };
    document.dispatchEvent(mouseEvent("mousemove", this.resizePosition));
    await nextFrame();
  }

  async finishMouseColumnResize(): Promise<void> {
    if (!this.resizePosition) throw new Error("No column resize is active");
    document.dispatchEvent(mouseEvent("mouseup", this.resizePosition));
    this.resizePosition = null;
    await nextFrame();
  }

  async resizeColumnWithMouse(label: string, delta: number): Promise<void> {
    await this.startMouseColumnResize(label);
    await this.moveMouseColumnResize(delta);
    await this.finishMouseColumnResize();
  }

  async resizeColumnWithTouch(label: string, delta: number): Promise<void> {
    const handle = this.resizeHandle(label).element();
    const start = center(handle);
    const end = { x: start.x + delta, y: start.y };
    handle.dispatchEvent(touchEvent("touchstart", handle, start));
    handle.dispatchEvent(touchEvent("touchmove", handle, end));
    await nextFrame();
    handle.dispatchEvent(touchEvent("touchend", handle, end));
    await nextFrame();
  }

  async resizeColumnWithKeyboard(label: string, keys: string): Promise<void> {
    this.resizeHandle(label).element().focus();
    await userEvent.keyboard(keys);
    await nextFrame();
  }

  async startMouseDrag(handle: Locator): Promise<void> {
    const start = center(handle.element());
    handle.element().dispatchEvent(mouseEvent("mousedown", start));
    this.mousePosition = { x: start.x, y: start.y + 8 };
    document.dispatchEvent(mouseEvent("mousemove", this.mousePosition));
    await nextFrame();
  }

  async moveMouseTo(target: Locator | Element, yOffset = 0): Promise<void> {
    const element = target instanceof Element ? target : target.element();
    const point = center(element);
    this.mousePosition = { x: point.x, y: point.y + yOffset };
    document.dispatchEvent(mouseEvent("mousemove", this.mousePosition));
    await nextFrame();
  }

  async moveMouseToColumnSectionEdge(section: "shown" | "hidden"): Promise<void> {
    if (!this.mousePosition) throw new Error("No mouse drag is active");
    const rect = (section === "shown" ? this.shownColumns : this.hiddenColumns).element().getBoundingClientRect();
    this.mousePosition = {
      x: this.mousePosition.x,
      y: section === "shown" ? rect.bottom - 5 : rect.top + 5,
    };
    document.dispatchEvent(mouseEvent("mousemove", this.mousePosition));
    await nextFrame();
  }

  async moveMouseNearFilterBottom(): Promise<void> {
    const rect = this.filterList.element().getBoundingClientRect();
    this.mousePosition = { x: rect.left + rect.width / 2, y: rect.bottom - 8 };
    document.dispatchEvent(mouseEvent("mousemove", this.mousePosition));
    await nextFrame();
    this.mousePosition = { x: rect.left + rect.width / 2, y: rect.bottom - 2 };
    document.dispatchEvent(mouseEvent("mousemove", this.mousePosition));
    await nextFrame();
  }

  async finishMouseDrag(): Promise<void> {
    if (!this.mousePosition) throw new Error("No mouse drag is active");
    document.dispatchEvent(mouseEvent("mouseup", this.mousePosition));
    this.mousePosition = null;
    await nextFrame();
    await nextFrame();
    await nextFrame();
    await nextFrame();
  }

  async cancelMouseDrag(): Promise<void> {
    await userEvent.keyboard("{Escape}");
    this.mousePosition = null;
    await nextFrame();
  }

  async keyboardMoveDown(handle: Locator): Promise<void> {
    handle.element().focus();
    await userEvent.keyboard("{Space}");
    await nextFrame();
    await userEvent.keyboard("{ArrowDown}");
    await nextFrame();
    await userEvent.keyboard("{Space}");
  }

  async keyboardCancelMove(handle: Locator): Promise<void> {
    handle.element().focus();
    await userEvent.keyboard("{Space}");
    await nextFrame();
    await userEvent.keyboard("{ArrowDown}");
    await nextFrame();
    await userEvent.keyboard("{Escape}");
  }

  startTouchDrag(handle: Locator): void {
    const element = handle.element();
    this.touchTarget = element;
    this.touchPosition = center(element);
    element.dispatchEvent(touchEvent("touchstart", element, this.touchPosition));
  }

  async moveTouchTo(target: Locator | Element, yOffset = 0): Promise<void> {
    if (!this.touchTarget) throw new Error("No touch drag is active");
    const element = target instanceof Element ? target : target.element();
    const point = center(element);
    this.touchPosition = { x: point.x, y: point.y + yOffset };
    this.touchTarget.dispatchEvent(touchEvent("touchmove", this.touchTarget, this.touchPosition));
    await nextFrame();
  }

  async finishTouchDrag(): Promise<void> {
    if (!this.touchTarget || !this.touchPosition) throw new Error("No touch drag is active");
    this.touchTarget.dispatchEvent(touchEvent("touchend", this.touchTarget, this.touchPosition));
    this.touchTarget = null;
    this.touchPosition = null;
    await nextFrame();
  }

  async tapDragHandle(handle: Locator): Promise<void> {
    const element = handle.element();
    const point = center(element);
    element.dispatchEvent(touchEvent("touchstart", element, point));
    element.dispatchEvent(touchEvent("touchend", element, point));
    await nextFrame();
  }
}
