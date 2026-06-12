const PUBLIC_API_BASE_URL = "/public";

/**
 * Fetch the public-view link for a published ELN record (or its published parent), via
 * `GET /public/publishedView/publiclink?globalId={globalId}`. The endpoint returns a
 * bare string body: the link when published, or an empty string when the record is not
 * published. Returns `null` for the empty-string (not-published) case.
 */
export async function getPublicLink(globalId: string): Promise<string | null> {
  const searchParams = new URLSearchParams();
  searchParams.set("globalId", globalId);

  const response = await fetch(
    `${PUBLIC_API_BASE_URL}/publishedView/publiclink?${searchParams.toString()}`,
    {
      method: "GET",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    },
  );

  if (!response.ok) {
    throw new Error(
      `Failed to fetch public link: ${response.statusText || response.status}`,
    );
  }

  const link = (await response.text()).trim();
  return link.length > 0 ? link : null;
}
