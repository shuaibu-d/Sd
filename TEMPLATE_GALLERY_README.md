# SD Studio template gallery

## Included templates
1. Announcement
2. Tip of the Day
3. Quote
4. Service Promo
5. Academy Course Promo
6. Testimonial
7. Project Showcase (device frame + project image slot)
8. Stats and Numbers
9. Event or Webinar
10. Hiring — Join Us
11. Carousel Cover + Content slide (one reusable layout definition)
12. Profile Picture (circle-safe monogram)

Every JSON template supports `1:1`, `4:5`, `9:16`, and `16:9`, with `light` and `dark` theme palettes.

## Gallery
The Studio tab now loads JSON templates from `assets/templates`, filters by category and ratio, renders low-resolution previews, caches thumbnails in an in-memory LRU cache, stores favourites in app preferences, and opens a template preview/editor entry screen when a card is tapped.

## Template editing
Template text values use `{placeholder}` tokens. The project image showcase uses the image slot key `project`. The current editor entry screen previews the selected template and ratio; the full text/image editing controls can be layered onto this entry screen in the next editor task.
