import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "./resizable";

const meta = {
  title: "DesignSystem/Resizable",
  component: ResizablePanelGroup,
  tags: ["autodocs"],
} satisfies Meta<typeof ResizablePanelGroup>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => (
    <ResizablePanelGroup
      orientation="horizontal"
      style={{ height: "200px", width: "400px", border: "1px solid var(--border)" }}
    >
      <ResizablePanel defaultSize={50}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>One</div>
      </ResizablePanel>
      <ResizableHandle />
      <ResizablePanel defaultSize={50}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>Two</div>
      </ResizablePanel>
    </ResizablePanelGroup>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const handle = canvas.getByRole("separator");
    expect(handle).toHaveAttribute("data-separator");
  },
};

export const WithHandleGrip: Story = {
  render: () => (
    <ResizablePanelGroup
      orientation="horizontal"
      style={{ height: "200px", width: "400px", border: "1px solid var(--border)" }}
    >
      <ResizablePanel defaultSize={25} minSize={15}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>
          Sidebar
        </div>
      </ResizablePanel>
      <ResizableHandle withHandle />
      <ResizablePanel defaultSize={75}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>
          Content
        </div>
      </ResizablePanel>
    </ResizablePanelGroup>
  ),
};

export const Vertical: Story = {
  render: () => (
    <ResizablePanelGroup
      orientation="vertical"
      style={{ height: "300px", width: "300px", border: "1px solid var(--border)" }}
    >
      <ResizablePanel defaultSize={50}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>Top</div>
      </ResizablePanel>
      <ResizableHandle withHandle />
      <ResizablePanel defaultSize={50}>
        <div style={{ display: "flex", height: "100%", alignItems: "center", justifyContent: "center" }}>Bottom</div>
      </ResizablePanel>
    </ResizablePanelGroup>
  ),
};
