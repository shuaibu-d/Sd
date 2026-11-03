# SD Studio v2 - stability and redesign

## Crash fixes
- Editor / video editor / quick preview recycled bitmaps that were still on screen -> instant crash on any button press. Removed.
- Saving a draft used REPLACE, which silently deleted its export history (foreign-key cascade). Now an upsert.
- Video export scene templates had the wrong ids (scene lookup failed, so video export could never start). Fixed.
- Logo overlay anchor was off-screen in exported videos. Now top-right, inset by the placement rules.
- Foreground service type for Android 10-14 (dataSync) and 15+ (mediaProcessing).
- Template loading no longer crashes the whole screen if one file is bad; results are cached.
- Text sizes no longer depend on phone screen density (exports look the same on every phone).
- Auto-fit text now really shrinks to fit; contact line shrinks to one line.
- Dark theme headings were forced navy on navy. Fixed.

## Brand
- All 12 post templates + 3 video scene templates redesigned on the official palette (Navy #101C2C, Teal #16A6A0, Gold #D9B45B, Cloud #F3F5F8, Slate #263445) and fonts.
- Logo slot added to every post template (top-right, safe zones per ratio). It was missing before.
- Official logo (transparent) is installed automatically on first launch: icon, full, light and dark versions. Light backgrounds recolour white logo parts to navy.
- App icon (adaptive + themed), notification icon, navy splash.

## UI/UX
- New theme (dark by default), custom icon set, 5-tab navigation: Home, Posts, Videos, Library, Brand. Planner and Settings open from Home.
- New Home (recent projects), Posts gallery, post editor (preview on top, Text/Photos/Design/Slides tabs), Videos hub, Library (Exports / Drafts).
- Video drafts can be reopened.
