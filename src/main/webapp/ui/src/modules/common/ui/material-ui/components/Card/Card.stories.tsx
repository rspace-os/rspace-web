/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Card/Card.stories.tsx
 * Upstream project license: MIT
 */
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { createBooleanArgType, createNumberArgType, createSelectArgType } from "../../argTypeTemplates";

const meta = {
  title: "Material UI/Surfaces/Card",
  component: Card,
  tags: ["autodocs"],
  parameters: { a11y: { test: "error" } },
  argTypes: {
    variant: createSelectArgType(["elevation", "outlined"], "elevation", "The variant to use.", "Appearance"),
    raised: createBooleanArgType("If true, the card will use raised styling.", false, "Appearance"),
    elevation: createNumberArgType("Shadow depth, corresponds to dp in the spec.", 1, 0, 24, "Appearance"),
    square: createBooleanArgType("If true, rounded corners are disabled.", false, "Appearance"),
    children: { control: false },
  },
} satisfies Meta<typeof Card>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Playground: Story = {
  args: {
    variant: "elevation",
    raised: false,
    elevation: 1,
    square: false,
  },
  render: (args) => (
    <Card {...args} sx={{ maxWidth: 345 }}>
      <CardContent>
        <Typography gutterBottom variant="h5" component="div">
          Playground Card
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Use the Controls panel to experiment with Card props like variant, elevation, and raised.
        </Typography>
      </CardContent>
      <CardActions>
        <Button size="small">Share</Button>
        <Button size="small">Learn More</Button>
      </CardActions>
    </Card>
  ),
};

export const Default: Story = {
  render: () => (
    <Card sx={{ maxWidth: 345 }}>
      <CardContent>
        <Typography gutterBottom variant="h5" component="div">
          Card Title
        </Typography>
        <Typography variant="body2" color="text.secondary">
          This is a default card with CardContent and Typography. It demonstrates the basic structure of a Material-UI
          Card component.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const MediaCard: Story = {
  render: () => (
    <Card sx={{ maxWidth: 345 }}>
      <CardMedia
        component="img"
        height="140"
        image="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='150' height='150'%3E%3Crect width='150' height='150' fill='%231976d2'/%3E%3Ccircle cx='75' cy='55' r='25' fill='white'/%3E%3Cpath d='M25 150v-20a50 50 0 0 1 100 0v20' fill='white'/%3E%3C/svg%3E"
        alt="Sample image"
      />
      <CardContent>
        <Typography gutterBottom variant="h5" component="div">
          Media Card
        </Typography>
        <Typography variant="body2" color="text.secondary">
          This card includes CardMedia to display images or videos along with content and actions.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const ActionCard: Story = {
  render: () => (
    <Card sx={{ maxWidth: 345 }}>
      <CardContent>
        <Typography gutterBottom variant="h5" component="div">
          Action Card
        </Typography>
        <Typography variant="body2" color="text.secondary">
          This card includes CardActions with interactive buttons for user engagement.
        </Typography>
      </CardContent>
      <CardActions>
        <Button size="small">Share</Button>
        <Button size="small">Learn More</Button>
      </CardActions>
    </Card>
  ),
};

export const OutlinedCard: Story = {
  render: () => (
    <Card variant="outlined" sx={{ maxWidth: 345 }}>
      <CardContent>
        <Typography gutterBottom variant="h5" component="div">
          Outlined Card
        </Typography>
        <Typography variant="body2" color="text.secondary">
          This card uses the variant="outlined" prop to display with a border instead of elevation shadow.
        </Typography>
      </CardContent>
    </Card>
  ),
};

export const InteractionTest: StoryObj<{ onAction: () => void }> = {
  args: { onAction: fn() },
  render: (args) => {
    return (
      <Card sx={{ maxWidth: 345 }}>
        <CardContent>
          <Typography gutterBottom variant="h5" component="div">
            Interactive Card
          </Typography>
          <Typography variant="body2" color="text.secondary">
            This card includes interactive elements for testing.
          </Typography>
        </CardContent>
        <CardActions>
          <Button size="small" onClick={args.onAction}>
            Share
          </Button>
          <Button size="small" onClick={args.onAction}>
            Learn More
          </Button>
        </CardActions>
      </Card>
    );
  },
  play: async ({ canvasElement, step, args }) => {
    const canvas = within(canvasElement);

    await step("Verify card structure renders", async () => {
      const heading = canvas.getByText("Interactive Card");
      await expect(heading).toBeInTheDocument();

      const description = canvas.getByText(/This card includes interactive elements/);
      await expect(description).toBeInTheDocument();
    });

    await step("Test card action buttons", async () => {
      const shareButton = canvas.getByRole("button", { name: /share/i });
      const learnMoreButton = canvas.getByRole("button", {
        name: /learn more/i,
      });

      await expect(shareButton).toBeInTheDocument();
      await expect(learnMoreButton).toBeInTheDocument();

      await userEvent.click(shareButton);
      await expect(args.onAction).toHaveBeenCalledTimes(1);
      await userEvent.click(learnMoreButton);
      await expect(args.onAction).toHaveBeenCalledTimes(2);
    });
  },
};
