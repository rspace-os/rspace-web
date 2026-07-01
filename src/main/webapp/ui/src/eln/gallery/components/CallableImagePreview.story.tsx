import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { CallableImagePreview, useImagePreview } from "./CallableImagePreview";

function TestComponent() {
  const { openImagePreview } = useImagePreview();

  return (
    <Stack spacing={2}>
      <Button onClick={() => openImagePreview("https://via.placeholder.com/800x600/0066cc/ffffff?text=Test+Image")}>
        {"\n        Open Image Preview\n      "}
      </Button>
      <Button
        onClick={() =>
          openImagePreview("https://via.placeholder.com/1200x800/cc6600/ffffff?text=Image+with+Caption", {
            caption: ["Test Image Caption", "This is a test image with multiple lines"],
          })
        }
      >
        {"\n        Open Image with Caption\n      "}
      </Button>
      <Button onClick={() => openImagePreview("https://via.placeholder.com/400x300/cc0066/ffffff?text=Small+Image")}>
        {"\n        Open Small Image\n      "}
      </Button>
    </Stack>
  );
}

export function CallableImagePreviewStory() {
  return (
    <CallableImagePreview>
      <TestComponent />
    </CallableImagePreview>
  );
}

function TestComponentWithLargeImage() {
  const { openImagePreview } = useImagePreview();

  return (
    <Button onClick={() => openImagePreview("https://via.placeholder.com/2000x1500/009900/ffffff?text=Large+Image")}>
      {"\n      Open Large Image Preview\n    "}
    </Button>
  );
}

export function CallableImagePreviewWithLargeImage() {
  return (
    <CallableImagePreview>
      <TestComponentWithLargeImage />
    </CallableImagePreview>
  );
}

function TestComponentWithErrorImage() {
  const { openImagePreview } = useImagePreview();

  return (
    <Button onClick={() => openImagePreview("https://invalid-url-that-should-fail.example.com/nonexistent.jpg")}>
      {"\n      Open Invalid Image\n    "}
    </Button>
  );
}

export function CallableImagePreviewWithError() {
  return (
    <CallableImagePreview>
      <TestComponentWithErrorImage />
    </CallableImagePreview>
  );
}

function TestComponentWithEmptyCaption() {
  const { openImagePreview } = useImagePreview();

  return (
    <Button
      onClick={() =>
        openImagePreview("https://via.placeholder.com/600x400/660099/ffffff?text=No+Caption", { caption: [] })
      }
    >
      {"\n      Open Image with Empty Caption\n    "}
    </Button>
  );
}

export function CallableImagePreviewWithEmptyCaption() {
  return (
    <CallableImagePreview>
      <TestComponentWithEmptyCaption />
    </CallableImagePreview>
  );
}
