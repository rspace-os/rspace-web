export const MOCK_YOUTUBE_ID = "dQw4w9WgXcQ";
export const MOCK_JOVE_ID = "12345";
export const MOCK_TIB_ID = "54321";

const mockPlayerPage = (label: string) => `<!doctype html><html><body>${label}</body></html>`;

export const MOCK_YOUTUBE_EMBED_BODY = mockPlayerPage("Mock YouTube Player");
export const MOCK_JOVE_EMBED_BODY = mockPlayerPage("Mock JoVE Player");
export const MOCK_TIB_EMBED_BODY = mockPlayerPage("Mock TIB AV-Portal Player");
