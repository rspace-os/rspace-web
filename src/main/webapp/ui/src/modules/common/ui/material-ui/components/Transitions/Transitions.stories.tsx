/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Transitions/Transitions.stories.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import Fade from "@mui/material/Fade";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grow from "@mui/material/Grow";
import type { PaperProps } from "@mui/material/Paper";
import Paper from "@mui/material/Paper";
import Slide from "@mui/material/Slide";
import Switch from "@mui/material/Switch";
import Typography from "@mui/material/Typography";
import Zoom from "@mui/material/Zoom";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { useArgs } from "storybook/preview-api";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta: Meta = {
  title: "Material UI/Utils/Transitions",
  component: Fade,
  parameters: {
    a11y: { test: "error" },
    layout: "padded",
  },
  tags: ["autodocs"],
  argTypes: {
    in: createBooleanArgType("If true, the component will transition in.", false, "State"),
    timeout: createNumberArgType("The duration of the transition, in milliseconds.", 300, 0, 3000, "Timing"),
    easing: createSelectArgType(
      ["ease-in", "ease-out", "ease-in-out", "linear"],
      "ease-in-out",
      "The easing function to use.",
      "Appearance",
    ),
    mountOnEnter: createBooleanArgType("If true, the component will be mounted when entering.", false, "Behavior"),
    unmountOnExit: createBooleanArgType("If true, the component will be unmounted when exiting.", false, "Behavior"),
  },
};

export default meta;
type Story = StoryObj;

/**
 * Interactive playground for Transitions.
 * Use the Controls panel to experiment with transition props.
 */
export const Playground: Story = {
  args: { in: true, timeout: 300 },
  render: (args) => {
    const [controls, updateArgs] = useArgs<{ in: boolean }>();
    const checked = controls.in;
    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel
          control={<Switch checked={checked} onChange={() => updateArgs({ in: !checked })} />}
          label="Show"
        />
        <Fade {...args} in={checked}>
          <Paper sx={{ m: 1, width: 100, height: 100 }} elevation={4}>
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                height: "100%",
              }}
            >
              <Typography>Content</Typography>
            </Box>
          </Paper>
        </Fade>
      </Box>
    );
  },
};

const TransitionBox = (props: PaperProps) => (
  <Paper sx={{ m: 1, width: 100, height: 100 }} elevation={4} {...props}>
    <Box
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
      }}
    >
      <Typography>Content</Typography>
    </Box>
  </Paper>
);

/**
 * Collapse transition - animate height
 */
export const CollapseTransition: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel control={<Switch checked={checked} onChange={() => setChecked(!checked)} />} label="Show" />
        <Box sx={{ display: "flex" }}>
          <Collapse in={checked}>
            <TransitionBox />
          </Collapse>
          <Collapse in={checked} collapsedSize={40}>
            <TransitionBox />
          </Collapse>
          <Collapse orientation="horizontal" in={checked}>
            <TransitionBox />
          </Collapse>
        </Box>
      </Box>
    );
  },
};

/**
 * Fade transition - animate opacity
 */
export const FadeTransition: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel control={<Switch checked={checked} onChange={() => setChecked(!checked)} />} label="Show" />
        <Box sx={{ display: "flex" }}>
          <Fade in={checked}>
            <TransitionBox />
          </Fade>
          <Fade in={checked} style={{ transitionDelay: checked ? "500ms" : "0ms" }}>
            <TransitionBox />
          </Fade>
        </Box>
      </Box>
    );
  },
};

/**
 * Grow transition - scale and fade
 */
export const GrowTransition: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel control={<Switch checked={checked} onChange={() => setChecked(!checked)} />} label="Show" />
        <Box sx={{ display: "flex" }}>
          <Grow in={checked}>
            <TransitionBox />
          </Grow>
          <Grow in={checked} style={{ transformOrigin: "0 0 0" }}>
            <TransitionBox />
          </Grow>
          <Grow in={checked} style={{ transformOrigin: "0 0 0" }} {...(checked ? { timeout: 1000 } : {})}>
            <TransitionBox />
          </Grow>
        </Box>
      </Box>
    );
  },
};

/**
 * Slide transition - slide from edge
 */
export const SlideTransition: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel control={<Switch checked={checked} onChange={() => setChecked(!checked)} />} label="Show" />
        <Box sx={{ display: "flex", gap: 2 }}>
          <Slide direction="up" in={checked} mountOnEnter unmountOnExit>
            <TransitionBox />
          </Slide>
          <Slide direction="right" in={checked} mountOnEnter unmountOnExit>
            <TransitionBox />
          </Slide>
          <Slide direction="down" in={checked} mountOnEnter unmountOnExit>
            <TransitionBox />
          </Slide>
          <Slide direction="left" in={checked} mountOnEnter unmountOnExit>
            <TransitionBox />
          </Slide>
        </Box>
      </Box>
    );
  },
};

/**
 * Zoom transition - scale from center
 */
export const ZoomTransition: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }}>
        <FormControlLabel control={<Switch checked={checked} onChange={() => setChecked(!checked)} />} label="Show" />
        <Box sx={{ display: "flex" }}>
          <Zoom in={checked}>
            <TransitionBox />
          </Zoom>
          <Zoom in={checked} style={{ transitionDelay: checked ? "200ms" : "0ms" }}>
            <TransitionBox />
          </Zoom>
          <Zoom in={checked} style={{ transitionDelay: checked ? "400ms" : "0ms" }}>
            <TransitionBox />
          </Zoom>
        </Box>
      </Box>
    );
  },
};

/**
 * All transitions comparison
 */
export const AllTransitions: Story = {
  render: () => {
    const [show, setShow] = useState(false);

    return (
      <Box>
        <Button variant="contained" onClick={() => setShow(!show)} sx={{ mb: 2 }}>
          Toggle All Transitions
        </Button>

        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(5, 1fr)",
            gap: 2,
          }}
        >
          <Box>
            <Typography variant="caption">Collapse</Typography>
            <Collapse in={show}>
              <TransitionBox />
            </Collapse>
          </Box>
          <Box>
            <Typography variant="caption">Fade</Typography>
            <Fade in={show}>
              <TransitionBox />
            </Fade>
          </Box>
          <Box>
            <Typography variant="caption">Grow</Typography>
            <Grow in={show}>
              <TransitionBox />
            </Grow>
          </Box>
          <Box>
            <Typography variant="caption">Slide</Typography>
            <Slide direction="up" in={show} mountOnEnter unmountOnExit>
              <TransitionBox />
            </Slide>
          </Box>
          <Box>
            <Typography variant="caption">Zoom</Typography>
            <Zoom in={show}>
              <TransitionBox />
            </Zoom>
          </Box>
        </Box>
      </Box>
    );
  },
};

export const InteractionTest: Story = {
  render: () => {
    const [checked, setChecked] = useState(false);

    return (
      <Box sx={{ height: 180 }} data-testid="transition-container">
        <FormControlLabel
          control={<Switch checked={checked} onChange={() => setChecked(!checked)} />}
          label="Show Transitions"
        />
        <Box sx={{ display: "flex", gap: 2 }}>
          <Collapse in={checked}>
            <Box data-testid="collapse-box">
              <TransitionBox />
            </Box>
          </Collapse>
          <Fade in={checked}>
            <TransitionBox data-testid="fade-box" />
          </Fade>
          <Grow in={checked}>
            <TransitionBox data-testid="grow-box" />
          </Grow>
        </Box>
      </Box>
    );
  },
  play: async ({ canvasElement, step }) => {
    const canvas = within(canvasElement);

    await step("Verify switch and labels render", async () => {
      const switchControl = canvas.getByRole("switch", {
        name: /show transitions/i,
      });
      const label = canvas.getByText("Show Transitions");

      expect(switchControl).toBeInTheDocument();
      expect(label).toBeInTheDocument();
      expect(switchControl).not.toBeChecked();
    });

    await step("Toggle transitions on", async () => {
      const switchControl = canvas.getByRole("switch", {
        name: /show transitions/i,
      });
      await userEvent.click(switchControl);

      expect(switchControl).toBeChecked();

      await waitFor(() => expect(canvas.getByTestId("fade-box")).toBeVisible());
    });

    await step("Verify transition elements are visible", async () => {
      // Check that transition content exists
      const contents = canvas.getAllByText("Content");
      expect(contents.length).toBeGreaterThan(0);
      await expect(canvas.getByTestId("fade-box")).toBeVisible();
    });

    await step("Toggle transitions off", async () => {
      const switchControl = canvas.getByRole("switch", {
        name: /show transitions/i,
      });
      await userEvent.click(switchControl);

      expect(switchControl).not.toBeChecked();

      await waitFor(() => expect(canvas.getByTestId("fade-box")).not.toBeVisible());
    });
  },
};
