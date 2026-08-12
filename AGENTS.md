# Android UI guidance

For this project, use the installed official Google Android Skills as the primary
implementation guidance for Navigation 3, edge-to-edge, and Compose theming.

- Preserve the existing Figma-derived 402dp visual system, product flows, and
  backend contracts unless the user explicitly requests a redesign.
- `mobile-android-design` is supplementary Material 3 guidance.
- Treat `ui-ux-pro-max` as an auxiliary UX and accessibility reviewer only. Do
  not use its GSAP, web-layout, hover, or generic motion snippets in this native
  Android application.
- The installed Compose `styles` skill requires alpha dependencies and
  experimental APIs. Do not enable it unless the user explicitly authorizes an
  experimental Compose/compileSdk upgrade.
