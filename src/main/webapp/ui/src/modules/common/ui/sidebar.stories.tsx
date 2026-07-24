import type { Meta, StoryObj } from "@storybook/react-vite";
import { HomeIcon, InboxIcon, SettingsIcon } from "lucide-react";
import { expect, userEvent } from "storybook/test";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "./sidebar";

const meta = {
  title: "DesignSystem/Sidebar",
  component: Sidebar,
  tags: ["autodocs"],
} satisfies Meta<typeof Sidebar>;

export default meta;

type Story = StoryObj<typeof meta>;

const items = [
  { title: "Home", icon: HomeIcon },
  { title: "Inbox", icon: InboxIcon },
  { title: "Settings", icon: SettingsIcon },
];

export const Default: Story = {
  render: () => (
    <SidebarProvider style={{ height: "400px" }}>
      <Sidebar>
        <SidebarHeader>
          <span style={{ padding: "0 8px", fontWeight: 600 }}>Acme Inc</span>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Navigation</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {items.map((item) => (
                  <SidebarMenuItem key={item.title}>
                    <SidebarMenuButton>
                      <item.icon />
                      <span>{item.title}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <span style={{ padding: "0 8px", fontSize: "12px" }}>v1.0.0</span>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", padding: "8px" }}>
          <SidebarTrigger />
          <span>Main content</span>
        </div>
      </SidebarInset>
    </SidebarProvider>
  ),
  play: async ({ canvasElement }) => {
    const trigger = canvasElement.querySelector<HTMLButtonElement>(
      '[data-sidebar="trigger"]',
    );
    expect(trigger).toBeInTheDocument();
    await userEvent.click(trigger as HTMLButtonElement);
  },
};

export const CollapsedToIcons: Story = {
  render: () => (
    <SidebarProvider defaultOpen={false} style={{ height: "400px" }}>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <span style={{ padding: "0 8px", fontWeight: 600 }}>Acme Inc</span>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Navigation</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {items.map((item) => (
                  <SidebarMenuItem key={item.title}>
                    <SidebarMenuButton tooltip={item.title}>
                      <item.icon />
                      <span>{item.title}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
      </Sidebar>
      <SidebarInset>
        <div style={{ display: "flex", alignItems: "center", gap: "8px", padding: "8px" }}>
          <SidebarTrigger />
          <span>Main content</span>
        </div>
      </SidebarInset>
    </SidebarProvider>
  ),
};
