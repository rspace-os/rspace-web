import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { CallablePdfPreview, usePdfPreview } from "./CallablePdfPreview";

function TestComponent() {
    const { openPdfPreview } = usePdfPreview();

    return (
        <Stack spacing={2}>
            <Button onClick={() => openPdfPreview("/test-documents/sample.pdf")}>Open PDF Preview</Button>
            <Button onClick={() => openPdfPreview("/test-documents/multi-page.pdf")}>Open Multi-Page PDF</Button>
            <Button onClick={() => openPdfPreview("/test-documents/single-page.pdf")}>Open Single Page PDF</Button>
        </Stack>
    );
}

export function CallablePdfPreviewStory() {
    return (
        <CallablePdfPreview>
            <TestComponent />
        </CallablePdfPreview>
    );
}

function TestComponentWithLargePdf() {
    const { openPdfPreview } = usePdfPreview();

    return <Button onClick={() => openPdfPreview("/test-documents/large-document.pdf")}>Open Large PDF</Button>;
}

export function CallablePdfPreviewWithLargePdf() {
    return (
        <CallablePdfPreview>
            <TestComponentWithLargePdf />
        </CallablePdfPreview>
    );
}

function TestComponentWithInvalidPdf() {
    const { openPdfPreview } = usePdfPreview();

    return <Button onClick={() => openPdfPreview("/test-documents/invalid.pdf")}>Open Invalid PDF</Button>;
}

export function CallablePdfPreviewWithError() {
    return (
        <CallablePdfPreview>
            <TestComponentWithInvalidPdf />
        </CallablePdfPreview>
    );
}

function TestComponentWithCorruptedPdf() {
    const { openPdfPreview } = usePdfPreview();

    return <Button onClick={() => openPdfPreview("/test-documents/corrupted.pdf")}>Open Corrupted PDF</Button>;
}

export function CallablePdfPreviewWithCorruptedFile() {
    return (
        <CallablePdfPreview>
            <TestComponentWithCorruptedPdf />
        </CallablePdfPreview>
    );
}
