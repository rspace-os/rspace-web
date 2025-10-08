# Gallery

## Overview

The Gallery is a sophisticated file management interface for RSpace that provides
a unified view of various file types stored both locally and on external
filesystems. It serves as the primary mechanism for browsing, organizing, and
managing files across different storage backends, with specialized views for
different file categories and comprehensive preview capabilities.

The Gallery powers file selection throughout RSpace, appearing as both a
standalone application and as picker dialogs embedded in Inventory as a means of attachment files to samples as well as in the ELN for referencing files in documents.

### Use Cases

The Gallery appears in several key contexts:

- **Standalone Gallery Application**: Full-featured file management interface
- **Inventory Attachments**: Selecting files to attach to inventory records
- **Stoichiometry**: For adding reagents to stoichiometry tables

Note that the old Gallery is still used (as of October 2025) in the ELN for:

- **Document Editor**: File picker for inserting images, documents, and other media

## Design

The Gallery handles two fundamentally different storage paradigms:

**Local Files**: Stored in RSpace's internal file system
- Have numeric IDs for reliable referencing
- Support full CRUD operations
- Integrated with RSpace's permission system
- Support versioning and audit trails

**Network Files (Filestores)**: External filesystem integration
- May only have path-based identification
- Operations depend on external system capabilities
- Require separate authentication flows
- Limited metadata and preview support

The Gallery organizes files into type-based sections that provide specialised
interfaces for different kinds of content. Local files are organised according
to their type, while NetworkFiles are organised by their filestore.

- **Images/Audio/Video**: Media files with preview capabilities
- **Documents**: Text documents, spreadsheets, presentations with Office integration
- **Chemistry**: Molecular structure files with Ketcher integration
- **DMPs**: Data Management Plans
- **NetworkFiles (Filestores)**: External filesystem integration (iRODS, etc.)
- **Snippets**: Text snippets for re-use across ELN documents
- **Exports**: Generated PDF documents and reports
- **Miscellaneous**: Other file types

## Core Components

### Main Architecture Components

- **`index.tsx`** - Main application shell with routing, theme, and layout
- **`Sidebar.tsx`** - Section navigation
- **`MainPanel.tsx`** - File grid view with drag & drop, responsive layout
- **`InfoPanel.tsx`** - File details, preview, and metadata display

### Hook-Based Data Management

- **`useGalleryListing.tsx`** - File fetching, caching, pagination, and folder navigation
- **`useGallerySelection.tsx`** - Multi-select state with keyboard/mouse interactions
- **`useGalleryActions.ts`** - File operations with error handling and progress tracking

### External Service Integration

- **`useCollabora.ts`** - Collabora Online document editing integration
- **`useOfficeOnline.ts`** - Microsoft Office Online editing capabilities
- **`CallableAsposePreview.tsx`** - Document preview using Aspose services
- **`CallableSnapGenePreview.tsx`** - DNA sequence file preview integration

## Routing

The Gallery uses a routing system that allows users to bookmark specific
folders, files, and sections. They cannot, however, share those links with other
users as each users has their open separate Gallery.

```
/gallery/{section}                    # Section root
/gallery/{section}/folder/{path}      # Folder navigation
/gallery/{section}/file/{id}          # Select file in its folder
```

## Dynamic Primary Action

The user interface dynamically provides a primary action based on the type of
the file and the available integration services. For example, a Word document
may show "Edit" if Office Online is available, or "Preview" if not (which will
use Aspose to generate a PDF). Many of these integrations are enabled as React
hooks and contexts allowing any part of the application to trigger the preview.
If this functionality is useful outside of the Gallery, then these hooks can be
extracted to a more global location. This is logic is in [primaryActionHooks.ts](./primaryActionHooks.ts), and in addition to the external services above includes

- **`CallableImagePreview.tsx`** - Image preview in a modal dialog
- **`CallablePdfPreview.tsx`** - PDF preview in a modal dialog
