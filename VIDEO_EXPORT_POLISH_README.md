# SD Studio video export polish

## Available options

- **Burn-in captions**: draws each scene's script text as a brand-colour lower-third caption. Caption timing follows the actual scene durations.
- **Teal progress bar**: draws a thin teal progress line at the bottom of the exported video.
- **Background music**: choose an audio file, set its volume, loop it to the project length or let it stop when the source ends, and optionally duck it during scenes that have a voice-over file. Music is downmixed to mono to match the mono voice track. Ducking is scene-based: 22% of the chosen music volume during voice-over scenes.

Use only music that you own or are licensed/otherwise permitted to use.

## Deliberately disabled options

Text entrance animations, cross-fade transitions, and final-mix loudness normalization are shown as disabled in the export screen. See `ARCHITECTURE.md` for why each is disabled and what a safe future implementation requires. The exporter does not pretend to apply these effects.

## Safe verification sequence

1. Export a short project with every polish option off. Confirm it behaves like the existing MVP exporter.
2. Enable only captions and export. Check that each caption changes at its scene boundary and remains above the lower safe area.
3. Enable only the teal progress bar. Check that it fills from left to right and stays on the bottom edge.
4. Choose a short audio file, set volume to 20%, and export with Loop enabled. Confirm music continues to the end.
5. Repeat with Loop disabled and confirm music ends when the source file ends.
6. Add voice-over to one scene and enable Auto-duck. Music should be quieter during that scene and return to the chosen level for scenes without voice.
7. Try the same short test at 720p before attempting a long 1080p export on a lower-end phone.
