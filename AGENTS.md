# Android UI guidance

For this project, use the installed official Google Android Skills as the primary
implementation guidance for Navigation 3, edge-to-edge, and Compose theming.

- Preserve the existing Figma-derived 402dp visual system, product flows, and
  backend contracts unless the user explicitly requests a redesign.
- **Figma visual fidelity is mandatory.** For every frontend screen or component
  change, treat the user-provided Figma node as the sole visual source of truth:
  reproduce its hierarchy, 402dp geometry, spacing, typography, colors, radii,
  icons/images, and interaction states. Do not substitute generic Material or
  agent-designed styling when the Figma design specifies a value.
- **Physical-device visual acceptance is mandatory.** After every frontend UI
  build, install it only on the connected physical phone and compare captured
  screenshots against the corresponding Figma node. Check safe-area placement,
  clipping, line wrapping, component dimensions, colors, margins, and motion.
  Fix every observed deviation before reporting the implementation complete.
- `mobile-android-design` is supplementary Material 3 guidance.
- Treat `ui-ux-pro-max` as an auxiliary UX and accessibility reviewer only. Do
  not use its GSAP, web-layout, hover, or generic motion snippets in this native
  Android application.
- The installed Compose `styles` skill requires alpha dependencies and
  experimental APIs. Do not enable it unless the user explicitly authorizes an
  experimental Compose/compileSdk upgrade.
