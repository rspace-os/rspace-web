import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { page, server, userEvent } from "vitest/browser";
import { emulateForcedColors, emulateHighContrast } from "@/__tests__/pageObjects/accessibility";
import { AlertDialog, AlertDialogContent, AlertDialogDescription, AlertDialogTitle } from "./alert-dialog";
import { Button } from "./button";
import { Card } from "./card";
import { Combobox, ComboboxContent, ComboboxInput, ComboboxItem, ComboboxList, ComboboxSeparator } from "./combobox";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "./dialog";
import { Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from "./menu";
import { Popover, PopoverContent, PopoverDescription, PopoverTitle, PopoverTrigger } from "./popover";
import { Progress } from "./progress";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "./resizable";
import { Separator } from "./separator";
import {
  Sidebar,
  SidebarContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarProvider,
} from "./sidebar";
import { Skeleton } from "./skeleton";
import { Switch } from "./switch";

const initialWindowWidth = window.innerWidth;
const labels = {
  actions: "Actions",
  apple: "Apple",
  archive: "Archive",
  banana: "Banana",
  bookings: "Bookings",
  dialogDescription: "Choose your preferences.",
  dialogTitle: "Preferences",
  discardDescription: "Your changes will be lost.",
  discardTitle: "Discard changes?",
  firstPanel: "First",
  loading: "Loading summary",
  progress: "Upload progress",
  popoverDescription: "A short summary of this booking.",
  popoverTitle: "Booking summary",
  rename: "Rename",
  save: "Save",
  secondPanel: "Second",
  separatorContainer: "Separator container",
  summary: "Summary",
  summaryCard: "Summary card",
  today: "Today",
} as const;

afterEach(() => {
  document.documentElement.classList.remove("dark");
  Object.defineProperty(window, "innerWidth", { configurable: true, value: initialWindowWidth });
  cleanup();
});

describe.skipIf(server.browser !== "chromium")("high contrast", () => {
  test("restores a visible keyboard focus outline in forced colors", async () => {
    await emulateForcedColors();
    render(<Button>{labels.save}</Button>);

    await userEvent.keyboard("{Tab}");

    const style = getComputedStyle(page.getByRole("button", { name: labels.save }).element());
    expect(style.outlineStyle).not.toBe("none");
    expect(Number.parseFloat(style.outlineWidth)).toBeGreaterThanOrEqual(2);
  });

  test("keeps the switch thumb visible in both states", async () => {
    await emulateForcedColors();
    render(
      <div>
        <Switch aria-label="Notifications off" />
        <Switch aria-label="Notifications on" defaultChecked />
      </div>,
    );

    const unchecked = page.getByRole("switch", { name: "Notifications off" }).element();
    const checked = page.getByRole("switch", { name: "Notifications on" }).element();
    // The thumb has no semantic role. Its data-slot is the component's stable visual hook.
    const uncheckedThumb = unchecked.querySelector<HTMLElement>('[data-slot="switch-thumb"]');
    const checkedThumb = checked.querySelector<HTMLElement>('[data-slot="switch-thumb"]');
    expect(uncheckedThumb).not.toBeNull();
    expect(checkedThumb).not.toBeNull();

    const uncheckedStyle = getComputedStyle(uncheckedThumb as HTMLElement);
    const checkedStyle = getComputedStyle(checkedThumb as HTMLElement);
    expect(Number.parseFloat(uncheckedStyle.borderTopWidth)).toBeGreaterThan(0);
    expect(Number.parseFloat(checkedStyle.borderTopWidth)).toBeGreaterThan(0);
    const uncheckedOffset =
      (uncheckedThumb as HTMLElement).getBoundingClientRect().left - unchecked.getBoundingClientRect().left;
    const checkedOffset =
      (checkedThumb as HTMLElement).getBoundingClientRect().left - checked.getBoundingClientRect().left;
    expect(uncheckedOffset).not.toBe(checkedOffset);
  });

  test("keeps a background-only separator visible", async () => {
    await emulateForcedColors();
    render(
      <section aria-label={labels.separatorContainer} className="bg-background p-2">
        <Separator />
      </section>,
    );

    const separator = page.getByRole("separator").element();
    const parent = page.getByLabelText(labels.separatorContainer).element();
    const separatorStyle = getComputedStyle(separator);
    const parentStyle = getComputedStyle(parent);
    const hasVisibleBackground = separatorStyle.backgroundColor !== parentStyle.backgroundColor;
    const hasVisibleBorder = Number.parseFloat(separatorStyle.borderTopWidth) > 0;
    expect(hasVisibleBackground || hasVisibleBorder).toBe(true);
  });

  test("keeps loading indicators and card edges visible", async () => {
    await emulateForcedColors();
    render(
      <div>
        <Skeleton role="status" aria-label={labels.loading} className="h-8" />
        <Progress aria-label={labels.progress} value={50} />
        <Card aria-label={labels.summaryCard}>{labels.summary}</Card>
      </div>,
    );

    const skeleton = page.getByRole("status", { name: labels.loading }).element();
    const progress = page.getByRole("progressbar", { name: labels.progress }).element();
    const track = progress.querySelector<HTMLElement>('[data-slot="progress-track"]');
    const indicator = progress.querySelector<HTMLElement>('[data-slot="progress-indicator"]');
    const card = page.getByLabelText(labels.summaryCard).element();
    expect(track).not.toBeNull();
    expect(indicator).not.toBeNull();

    expect(Number.parseFloat(getComputedStyle(skeleton).borderTopWidth)).toBeGreaterThan(0);
    expect(Number.parseFloat(getComputedStyle(track as HTMLElement).borderTopWidth)).toBeGreaterThan(0);
    expect(getComputedStyle(indicator as HTMLElement).backgroundColor).not.toBe(
      getComputedStyle(track as HTMLElement).backgroundColor,
    );
    expect(Number.parseFloat(getComputedStyle(card).borderTopWidth)).toBeGreaterThan(0);
  });

  test("keeps dialog and alert dialog edges visible", async () => {
    await emulateForcedColors();
    const view = render(
      <Dialog defaultOpen>
        <DialogContent>
          <DialogTitle>{labels.dialogTitle}</DialogTitle>
          <DialogDescription>{labels.dialogDescription}</DialogDescription>
        </DialogContent>
      </Dialog>,
    );

    const dialog = page.getByRole("dialog", { name: labels.dialogTitle }).element();
    expect(Number.parseFloat(getComputedStyle(dialog).borderTopWidth)).toBeGreaterThan(0);

    view.unmount();
    render(
      <AlertDialog defaultOpen>
        <AlertDialogContent>
          <AlertDialogTitle>{labels.discardTitle}</AlertDialogTitle>
          <AlertDialogDescription>{labels.discardDescription}</AlertDialogDescription>
        </AlertDialogContent>
      </AlertDialog>,
    );

    const alertDialog = page.getByRole("alertdialog", { name: labels.discardTitle }).element();
    expect(Number.parseFloat(getComputedStyle(alertDialog).borderTopWidth)).toBeGreaterThan(0);
  });

  test("keeps popover edges visible", async () => {
    await emulateForcedColors();
    render(
      <Popover defaultOpen>
        <PopoverTrigger>{labels.summary}</PopoverTrigger>
        <PopoverContent>
          <PopoverTitle>{labels.popoverTitle}</PopoverTitle>
          <PopoverDescription>{labels.popoverDescription}</PopoverDescription>
        </PopoverContent>
      </Popover>,
    );

    const popover = page.getByRole("dialog", { name: labels.popoverTitle }).element();
    expect(Number.parseFloat(getComputedStyle(popover).borderTopWidth)).toBeGreaterThan(0);
  });

  test("shows only the virtually focused combobox option", async () => {
    await emulateForcedColors();
    render(
      <Combobox items={["Apple", "Banana"]}>
        <ComboboxInput aria-label="Fruit" />
        <ComboboxContent>
          <ComboboxList>
            <ComboboxItem value={labels.apple}>{labels.apple}</ComboboxItem>
            <ComboboxSeparator />
            <ComboboxItem value={labels.banana}>{labels.banana}</ComboboxItem>
          </ComboboxList>
        </ComboboxContent>
      </Combobox>,
    );

    await page.getByRole("combobox", { name: "Fruit" }).click();
    await userEvent.keyboard("{ArrowDown}");

    const highlighted = page.getByRole("option", { name: labels.apple }).element();
    const resting = page.getByRole("option", { name: labels.banana }).element();
    const panel = document.querySelector<HTMLElement>('[data-slot="combobox-content"]');
    const separator = document.querySelector<HTMLElement>('[data-slot="combobox-separator"]');
    expect(panel).not.toBeNull();
    expect(separator).not.toBeNull();
    expect(highlighted).toHaveAttribute("data-highlighted");
    const highlightedStyle = getComputedStyle(highlighted);
    const restingStyle = getComputedStyle(resting);
    expect(highlightedStyle.outlineStyle).not.toBe("none");
    expect(Number.parseFloat(highlightedStyle.outlineWidth)).toBeGreaterThanOrEqual(2);
    expect(restingStyle.outlineStyle).toBe("none");
    expect(Number.parseFloat(getComputedStyle(panel as HTMLElement).borderTopWidth)).toBeGreaterThan(0);
    expect(getComputedStyle(separator as HTMLElement).backgroundColor).not.toBe(
      getComputedStyle((separator as HTMLElement).parentElement as HTMLElement).backgroundColor,
    );
  });

  test("keeps menu and resize separators visible", async () => {
    await emulateForcedColors();
    const view = render(
      <Menu defaultOpen>
        <MenuTrigger>{labels.actions}</MenuTrigger>
        <MenuContent>
          <MenuItem>{labels.rename}</MenuItem>
          <MenuSeparator />
          <MenuItem>{labels.archive}</MenuItem>
        </MenuContent>
      </Menu>,
    );

    const menuSeparator = page.getByRole("separator").element();
    expect(getComputedStyle(menuSeparator).backgroundColor).not.toBe(
      getComputedStyle(menuSeparator.parentElement as HTMLElement).backgroundColor,
    );

    view.unmount();
    render(
      <ResizablePanelGroup orientation="horizontal" style={{ height: "100px", width: "300px" }}>
        <ResizablePanel defaultSize={50}>{labels.firstPanel}</ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel defaultSize={50}>{labels.secondPanel}</ResizablePanel>
      </ResizablePanelGroup>,
    );

    const resizeSeparator = page.getByRole("separator").element();
    const handle = resizeSeparator.firstElementChild as HTMLElement;
    expect(getComputedStyle(resizeSeparator).backgroundColor).not.toBe(
      getComputedStyle(resizeSeparator.parentElement as HTMLElement).backgroundColor,
    );
    expect(getComputedStyle(handle).backgroundColor).not.toBe(
      getComputedStyle(resizeSeparator.parentElement as HTMLElement).backgroundColor,
    );
  });

  test("keeps the floating sidebar edge and active items visible", async () => {
    await emulateForcedColors();
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 1024 });
    render(
      <SidebarProvider>
        <Sidebar variant="floating">
          <SidebarContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton isActive>{labels.bookings}</SidebarMenuButton>
                <SidebarMenuSub>
                  <SidebarMenuSubItem>
                    <SidebarMenuSubButton href="#today" isActive>
                      {labels.today}
                    </SidebarMenuSubButton>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarContent>
        </Sidebar>
      </SidebarProvider>,
    );

    const sidebarPanel = document.querySelector<HTMLElement>('[data-slot="sidebar-inner"]');
    const activeButton = document.querySelector<HTMLElement>('[data-slot="sidebar-menu-button"]');
    const activeSubButton = document.querySelector<HTMLElement>('[data-slot="sidebar-menu-sub-button"]');
    expect(sidebarPanel).not.toBeNull();
    expect(activeButton).not.toBeNull();
    expect(activeSubButton).not.toBeNull();
    expect(Number.parseFloat(getComputedStyle(sidebarPanel as HTMLElement).borderTopWidth)).toBeGreaterThan(0);
    expect(getComputedStyle(activeButton as HTMLElement).outlineStyle).not.toBe("none");
    expect(getComputedStyle(activeSubButton as HTMLElement).outlineStyle).not.toBe("none");
  });

  test("overrides the light contrast tokens without forced colors", async () => {
    await emulateHighContrast();

    const style = getComputedStyle(document.documentElement);
    expect(style.getPropertyValue("--muted-foreground").trim()).toBe("oklch(0.46 0.021 213.5)");
    expect(style.getPropertyValue("--border").trim()).toBe("oklch(0.665 0.005 214.3)");
    expect(style.getPropertyValue("--ring").trim()).toBe("oklch(0.46 0.021 213.5)");
  });

  test("overrides the dark border token without changing muted text", async () => {
    document.documentElement.classList.add("dark");
    await emulateHighContrast();

    const style = getComputedStyle(document.documentElement);
    expect(style.getPropertyValue("--border").trim()).toBe("oklch(1 0 0 / 40%)");
    expect(style.getPropertyValue("--muted-foreground").trim()).toBe("oklch(0.723 0.014 214.4)");
  });
});
