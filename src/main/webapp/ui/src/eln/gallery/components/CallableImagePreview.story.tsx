import React from "react";
import {
  CallableImagePreview,
  useImagePreview,
} from "./CallableImagePreview";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";

function TestComponent() {
  const { openImagePreview } = useImagePreview();

  return (
    <Stack spacing={2}>
      <Button
        onClick={() =>
          openImagePreview("https://via.placeholder.com/800x600/0066cc/ffffff?text=Test+Image")
        }
      >
        Open Image Preview
      </Button>
      <Button
        onClick={() =>
          openImagePreview(
            "https://via.placeholder.com/1200x800/cc6600/ffffff?text=Image+with+Caption",
            {
              caption: ["Test Image Caption", "This is a test image with multiple lines"],
            }
          )
        }
      >
        Open Image with Caption
      </Button>
      <Button
        onClick={() =>
          openImagePreview("https://via.placeholder.com/400x300/cc0066/ffffff?text=Small+Image")
        }
      >
        Open Small Image
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
    <Button
      onClick={() =>
        openImagePreview("https://via.placeholder.com/2000x1500/009900/ffffff?text=Large+Image")
      }
    >
      Open Large Image Preview
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
    <Button
      onClick={() =>
        openImagePreview("https://invalid-url-that-should-fail.example.com/nonexistent.jpg")
      }
    >
      Open Invalid Image
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
        openImagePreview(
          "https://via.placeholder.com/600x400/660099/ffffff?text=No+Caption",
          { caption: [] }
        )
      }
    >
      Open Image with Empty Caption
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
