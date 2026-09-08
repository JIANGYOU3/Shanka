---
name: figma-design
description: Fetch and implement Figma designs for this app. Use when the user shares a figma.com design link (with node-id), asks to implement/restore/compare a Figma node or design (设计稿/还原/对照), or needs Figma design data (layout, colors, typography, spacing) for any UI change. Covers URL parsing, the Figma REST API fallback when mcp__figma__* tools are not loaded in the session, node PNG rendering for visual comparison, and the 402dp design-canvas mapping. Not for editing Figma files or the desktop Dev Mode MCP.
---

# Figma design access & implementation

Get the real design data and rendered image first; never implement from a
screenshot alone or from guessed values.

## Access paths, in order

1. **MCP tools** — if `mcp__figma__*` tools are loaded this session, prefer them.
2. **REST API fallback** — the MCP server is configured at user scope in
   `~/.zcode/cli/config.json` (`mcp.servers.figma`). The token is a secret:
   read it from that file, never hardcode or commit it.

   ```bash
   TOKEN=$(sed -n 's/.*--figma-api-key=\([^"]*\).*/\1/p' ~/.zcode/cli/config.json)
   curl -s -H "X-Figma-Token: $TOKEN" "https://api.figma.com/v1/files/<FILE_KEY>/nodes?ids=<ID>&depth=2"
   ```

## URL parsing

`https://www.figma.com/design/<FILE_KEY>/<file-name>?node-id=184-616`

- File key: the path segment after `/design/`.
- Node id: `184-616` in the URL becomes `184:616` in API calls.

## Key endpoints

- **Node subtree**: `GET /v1/files/{key}/nodes?ids={id}&depth=2`. Always pass
  `depth` — full subtrees can be megabytes. Fetch deeper nodes by their own ids.
- **Render to PNG**: `GET /v1/images/{key}?ids={id}&format=png&scale=2` returns
  a JSON with a signed S3 URL. Download it immediately (URLs expire within
  hours) and Read it to see the design.
- AutoLayout maps to Compose: `layoutMode` → Row/Column, `itemSpacing` →
  `Arrangement.spacedBy`, `padding*` → padding, `cornernerRadius` → shape.
- Node JSON `fills[].color` carries the resolved RGBA (0–1 floats) even when the
  fill is bound to a variable via `boundVariables`, so exact colors are always
  available without the variables endpoint.
- Text nodes carry `style` with `fontFamily`, `fontWeight`, `fontSize`,
  `lineHeightPx`, `letterSpacing` — reproduce these, don't approximate.

## 402dp mapping (project rules)

- The design canvas is 402dp wide; rendered PNGs at scale=2 are 804px wide.
  Divide px by 2 for dp.
- Existing screens scale visuals by `compactScale = screenWidthDp / 402f`
  (see `HomeScreen.kt`) — follow the established pattern.
- Figma fidelity rules in the workspace AGENTS.md override generic Material
  styling.
- After implementing, physical-device screenshot comparison is mandatory —
  use the `android-device-debugging` skill.

## Pitfalls

- `node-id` uses `-` in URLs but `:` in API calls.
- Avoid `geometry=paths` unless vector data is required; it bloats responses.
- Render the frame itself, not the page — page renders omit fixed-position
  header/nav layering.
