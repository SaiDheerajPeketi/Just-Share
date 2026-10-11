# System-safe navigation boundary

The target-36 app now applies `safeDrawingPadding` to the NavHost inside its existing full-window themed background. History header, bottom navigation, settings and sharing-route content therefore stay inside status/navigation/cutout/IME safe bounds. Existing child status/safe-drawing padding is retained: Compose inset padding consumes parent-applied insets, preventing duplicate application. Full-window background painting and existing appearance preferences remain unchanged.

On API24–25, where dark navigation glyphs are unsupported, the navigation backdrop uses the existing dark surface (#141316). API26+ keeps the existing selected-theme background and icon policy. No backend, billing, provider, feature, version or navigation-route changes.

Source evidence: `MainActivity.kt` NavHost modifier; `ui/theme/Theme.kt` legacy navigation branch; `navigation/NavGraph.kt` forwards its modifier to NavHost. Existing `BackBar`/Settings status padding and permission safe padding remain intact. Official inset consumption behavior: https://developer.android.com/develop/ui/compose/system/insets-ui#inset-consumption .

Validation: `git diff --check` PASS; source comparison confirms only the two bounded changes. Native compilation, instrumented layout and physical visual checks remain UNRUN. Root owns those checks; API33 phone evidence cannot establish API24–25 or enforced Android15+ edge-to-edge. Check History/Home/Settings/file selection, light/dark preference versus system mode, gesture/three-button navigation, cutouts and keyboard before runtime acceptance.
