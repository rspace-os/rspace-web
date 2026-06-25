/** A single field within an `ApiDocument`. */
export interface ApiDocumentField {
  id: number;
  name: string;
  type: string;
  content?: string;
}

/**
 * Hand-written subset of `com.researchspace.api.v1.model.ApiDocument`.
 * Narrowed to the fields the current specs touch — extend as more tests are added.
 *
 * TODO: generate from `src/main/webapp/resources/rspace_api_specs_*.yaml`
 * instead of hand-maintaining this once more than a couple of DTOs are needed.
 */
export interface ApiDocument {
  id: number;
  globalId: string;
  name: string;
  parentFolderId?: number;
  tags?: string;
  fields: ApiDocumentField[];
}

/** Request body for `POST /api/v1/documents`. */
export interface ApiDocumentCreateRequest {
  name: string;
  parentFolderId?: number;
  tags?: string;
}
