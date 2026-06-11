# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: eln/gallery/components/ActionsMenu.spec.tsx >> ActionsMenu >> Should have no axe violations >> Should have no axe violations
- Location: src/eln/gallery/components/ActionsMenu.spec.tsx:555:5

# Error details

```
Error: expect(received).toEqual(expected) // deep equality

- Expected  -  1
+ Received  + 58

- Array []
+ Array [
+   Object {
+     "description": "Ensure the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds",
+     "help": "Elements must meet minimum color contrast ratio thresholds",
+     "helpUrl": "https://dequeuniversity.com/rules/axe/4.11/color-contrast?application=playwright",
+     "id": "color-contrast",
+     "impact": "serious",
+     "nodes": Array [
+       Object {
+         "all": Array [],
+         "any": Array [
+           Object {
+             "data": Object {
+               "bgColor": "#dfe0e1",
+               "contrastRatio": 1.9,
+               "expectedContrastRatio": "4.5:1",
+               "fgColor": "#a2a3a5",
+               "fontSize": "9.8pt (13px)",
+               "fontWeight": "bold",
+               "messageKey": null,
+             },
+             "id": "color-contrast",
+             "impact": "serious",
+             "message": "Element has insufficient color contrast of 1.9 (foreground color: #a2a3a5, background color: #dfe0e1, font size: 9.8pt (13px), font weight: bold). Expected contrast ratio of 4.5:1",
+             "relatedNodes": Array [
+               Object {
+                 "html": "<button class=\"MuiButtonBase-root MuiButton-root MuiButton-contained MuiButton-sizeSmall MuiButton-colorCallToAction MuiButton-disableElevation css-9rqvkv\" tabindex=\"0\" type=\"button\" aria-haspopup=\"menu\" aria-expanded=\"false\">",
+                 "target": Array [
+                   "button",
+                 ],
+               },
+             ],
+           },
+         ],
+         "failureSummary": "Fix any of the following:
+   Element has insufficient color contrast of 1.9 (foreground color: #a2a3a5, background color: #dfe0e1, font size: 9.8pt (13px), font weight: bold). Expected contrast ratio of 4.5:1",
+         "html": "<button class=\"MuiButtonBase-root MuiButton-root MuiButton-contained MuiButton-sizeSmall MuiButton-colorCallToAction MuiButton-disableElevation css-9rqvkv\" tabindex=\"0\" type=\"button\" aria-haspopup=\"menu\" aria-expanded=\"false\">",
+         "impact": "serious",
+         "none": Array [],
+         "target": Array [
+           "button",
+         ],
+       },
+     ],
+     "tags": Array [
+       "cat.color",
+       "wcag2aa",
+       "wcag143",
+       "TTv5",
+       "TT13.c",
+       "EN-301-549",
+       "EN-9.1.4.3",
+       "ACT",
+       "RGAAv4",
+       "RGAA-3.2.1",
+     ],
+   },
+ ]
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic [ref=e3]:
    - button "Actions" [ref=e4]:
      - img [ref=e6]
      - text: Actions
    - status [ref=e8]: 1 file selected
  - region "There are currently 0 alerts."
```

# Test source

```ts
  145 |         await expect(
  146 |           page.getByRole("menuitem", { name: /download/i }),
  147 |         ).toBeDisabled({ timeout: 5000 });
  148 |       },
  149 |       "the Share option should be visible": async () => {
  150 |         await expect(page.getByRole("menuitem", { name: /share/i })).toBeVisible(
  151 |           { timeout: 5000 },
  152 |         );
  153 |       },
  154 |       "the Share option should be disabled": async () => {
  155 |         await expect(page.getByRole("menuitem", { name: /share/i })).toBeDisabled(
  156 |           {
  157 |             timeout: 5000,
  158 |           },
  159 |         );
  160 |       },
  161 |       "the Share option should be enabled": async () => {
  162 |         await expect(page.getByRole("menuitem", { name: /share/i })).toBeEnabled(
  163 |           {
  164 |             timeout: 5000,
  165 |           },
  166 |         );
  167 |       },
  168 |       "the share dialog for the selected snippet should be visible": async () => {
  169 |         await expect(
  170 |           page.getByRole("dialog", { name: /Share My Snippet/i }),
  171 |         ).toBeVisible({ timeout: 5000 });
  172 |       },
  173 |       "the share dialog for two snippets should be visible": async () => {
  174 |         await expect(
  175 |           page.getByRole("dialog", { name: /Share 2 snippets/i }),
  176 |         ).toBeVisible({ timeout: 5000 });
  177 |       },
  178 |       "share info should be requested for both selected snippets": () => {
  179 |         const requestedPaths = networkRequests.map((url) => url.pathname);
  180 |         expect(requestedPaths).toContain("/api/v1/share/document/3");
  181 |         expect(requestedPaths).toContain("/api/v1/share/document/5");
  182 |         return Promise.resolve();
  183 |       },
  184 |       "the Share disabled reason for missing global IDs should be visible":
  185 |         async () => {
  186 |           const shareMenuItem = page.getByRole("menuitem", { name: /share/i });
  187 |           await expect(shareMenuItem).toContainText(
  188 |             /Cannot share snippets that are missing global IDs\./i,
  189 |             { timeout: 5000 },
  190 |           );
  191 |         },
  192 |       "the Share disabled reason for shared folder ownership should be visible":
  193 |         async () => {
  194 |           const shareMenuItem = page.getByRole("menuitem", { name: /share/i });
  195 |           await expect(shareMenuItem).toContainText(
  196 |             /Only owners of the snippet can change its share settings\./i,
  197 |             { timeout: 5000 },
  198 |           );
  199 |         },
  200 |       "the Share disabled loading reason should be visible": async () => {
  201 |         const shareMenuItem = page.getByRole("menuitem", { name: /share/i });
  202 |         await expect(shareMenuItem).toContainText(/Loading user information\.\.\./i, {
  203 |           timeout: 5000,
  204 |         });
  205 |       },
  206 |       "a share success alert should be visible": async () => {
  207 |         await expect(
  208 |           page.getByRole("alert").filter({
  209 |             hasText: /Shares updated successfully\./i,
  210 |           }),
  211 |         ).toContainText(
  212 |           /Shares updated successfully\./i,
  213 |           { timeout: 5000 },
  214 |         );
  215 |       },
  216 |       "the share dialog should close": async () => {
  217 |         await expect(page.getByRole("dialog", { name: /Share/i })).not.toBeVisible({
  218 |           timeout: 5000,
  219 |         });
  220 |       },
  221 |       "there shouldn't be any axe violations": async () => {
  222 |         const accessibilityScanResults = await new AxeBuilder({
  223 |           page,
  224 |         }).analyze();
  225 |         expect(
  226 |           accessibilityScanResults.violations.filter((v) => {
  227 |             /*
  228 |              * These violations are expected in component tests as we're not rendering
  229 |              * a complete page with proper document structure:
  230 |              *
  231 |              * 1. MUI DataGrid renders its immediate children with role=presentation,
  232 |              *    which Firefox considers to be a violation
  233 |              * 2. Component tests don't have main landmarks as they're isolated components
  234 |              * 3. Component tests typically don't have h1 headings as they're not full pages
  235 |              * 4. Content not in landmarks is expected in component testing context
  236 |              */
  237 |             return (
  238 |               v.description !==
  239 |                 "Ensure elements with an ARIA role that require child roles contain them" &&
  240 |               v.id !== "landmark-one-main" &&
  241 |               v.id !== "page-has-heading-one" &&
  242 |               v.id !== "region"
  243 |             );
  244 |           }),
> 245 |         ).toEqual([]);
      |           ^ Error: expect(received).toEqual(expected) // deep equality
  246 |       },
  247 |       "the 'Move to S3' option should be visible": async () => {
  248 |         await expect(
  249 |           page.getByRole("menuitem", { name: /move to s3/i }),
  250 |         ).toBeVisible({ timeout: 5000 });
  251 |       },
  252 |       "the 'Move to iRODS' option should not be in the document": async () => {
  253 |         await expect(
  254 |           page.getByRole("menuitem", { name: /move to irods/i }),
  255 |         ).not.toBeAttached({ timeout: 5000 });
  256 |       },
  257 |       "the 'Move to S3' option should not be in the document": async () => {
  258 |         await expect(
  259 |           page.getByRole("menuitem", { name: /move to s3/i }),
  260 |         ).not.toBeAttached({ timeout: 5000 });
  261 |       },
  262 |       "there shouldn't be any MUI styling errors": async () => {
  263 |         const muiErrors = consoleErrors.filter((msg) =>
  264 |           /MUI.*Unsupported|MUI error #9/i.test(msg),
  265 |         );
  266 |         expect(muiErrors).toHaveLength(0);
  267 |       },
  268 |     });
  269 |   },
  270 |   networkRequests: async ({}, use) => {
  271 |     await use([]);
  272 |   },
  273 |   consoleErrors: async ({}, use) => {
  274 |     await use([]);
  275 |   },
  276 | 
  277 | });
  278 | feature.beforeEach(async ({ router, page, networkRequests, consoleErrors }) => {
  279 |   page.on("request", (request) => {
  280 |     networkRequests.push(new URL(request.url()));
  281 |   });
  282 |   page.on("console", (msg) => {
  283 |     if (msg.type() === "error") consoleErrors.push(msg.text());
  284 |   });
  285 |   await router.route("/session/ajax/analyticsProperties", (route) => {
  286 |     return route.fulfill({
  287 |       status: 200,
  288 |       contentType: "application/json",
  289 |       body: JSON.stringify({
  290 |         analyticsEnabled: false,
  291 |       }),
  292 |     });
  293 | 
  294 |   });
  295 |   await router.route("/userform/ajax/preference*", (route) => {
  296 |     return route.fulfill({
  297 |       status: 200,
  298 |       contentType: "application/json",
  299 |       body: JSON.stringify({}),
  300 |     });
  301 | 
  302 |   });
  303 |   await router.route("/deploymentproperties/ajax/property*", (route) => {
  304 |     return route.fulfill({
  305 |       status: 200,
  306 |       contentType: "application/json",
  307 |       body: JSON.stringify(false),
  308 |     });
  309 | 
  310 |   });
  311 |   await router.route("/collaboraOnline/supportedExts", (route) => {
  312 |     return route.fulfill({
  313 |       status: 200,
  314 |       contentType: "application/json",
  315 |       body: JSON.stringify({}),
  316 |     });
  317 | 
  318 |   });
  319 |   await router.route("/integration/integrationInfo?name=RAID", (route) => {
  320 |     return route.fulfill({
  321 |       status: 200,
  322 |       contentType: "application/json",
  323 |       body: JSON.stringify({
  324 |         success: true,
  325 |         data: {
  326 |           name: "RAID",
  327 |           displayName: "RAiD",
  328 |           available: false,
  329 |           enabled: false,
  330 |           oauthConnected: false,
  331 |           options: {
  332 |             RAID_CONFIGURED_SERVERS: [],
  333 |           },
  334 |         },
  335 |       }),
  336 |     });
  337 | 
  338 |   });
  339 |   await router.route("/officeOnline/supportedExts", (route) => {
  340 |     return route.fulfill({
  341 |       status: 200,
  342 |       contentType: "application/json",
  343 |       body: JSON.stringify({}),
  344 |     });
  345 | 
```