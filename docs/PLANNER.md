# Planner and Caption Editor

## Included
- Local Room-backed Planner with month and list views.
- Platforms: Instagram, Facebook, TikTok, LinkedIn, YouTube, WhatsApp Status and X.
- Planner statuses: Idea, Draft, Ready and Posted.
- Optional project link and per-item posting checklist.
- Device-local reminder notifications, requesting `POST_NOTIFICATIONS` at runtime on Android 13+.
- Caption editor attached to a saved project, with live character count, reusable Brand Kit caption templates and hashtag groups, contact line, CTA and clipboard copy.
- Existing Brand tab remains the management surface for caption templates and hashtag groups.
- Room migration 1 -> 2 adds project captions and planner reminder/checklist fields while preserving existing local data.

## Local-only behavior
Planner items, project captions and Brand Kit content are stored in the app's Room database. There is no account, cloud sync or publishing API.

## Reminder behavior
Reminders use `AlarmManager.setAndAllowWhileIdle` and a local notification channel. Android 13+ notification permission is requested only when a user saves an item with a reminder. If permission is declined, the planner item is still saved but no notification is scheduled.
