import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { CallableImagePreview, useImagePreview } from "./CallableImagePreview";

// Inline fixtures also load when MSW uses fetch/XHR interception in Firefox.
function imageFixture(width: number, height: number, color: string) {
  return `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="${color}"/></svg>`,
  )}`;
}

function TestComponent() {
  const { openImagePreview } = useImagePreview();

  return (
    <Stack spacing={2}>
      <Button onClick={() => openImagePreview(imageFixture(800, 600, "#0066cc"))}>{"Open Image Preview"}</Button>
      <Button
        onClick={() =>
          openImagePreview(imageFixture(1200, 800, "#cc6600"), {
            caption: ["Test Image Caption", "This is a test image with multiple lines"],
          })
        }
      >
        {"Open Image with Caption"}
      </Button>
      <Button onClick={() => openImagePreview(imageFixture(400, 300, "#cc0066"))}>{"Open Small Image"}</Button>
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
    <Button onClick={() => openImagePreview(imageFixture(2000, 1500, "#009900"))}>{"Open Large Image Preview"}</Button>
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

  return <Button onClick={() => openImagePreview("data:image/png;base64,invalid")}>{"Open Invalid Image"}</Button>;
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
    <Button onClick={() => openImagePreview(imageFixture(600, 400, "#660099"), { caption: [] })}>
      {"Open Image with Empty Caption"}
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
