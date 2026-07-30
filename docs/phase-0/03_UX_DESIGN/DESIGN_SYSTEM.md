# Visual Design System

## Direction

“Calm financial control”: generous spacing, restrained colour, clear totals and minimal decoration.

## Colour tokens

### Light
- Primary: `#2F6B4F`
- On primary: `#FFFFFF`
- Primary container: `#D8F3E3`
- Surface: `#F7FAF8`
- Surface elevated: `#FFFFFF`
- Text primary: `#172019`
- Text secondary: `#536158`
- Border: `#D8E2DB`
- Expense: `#B3261E`
- Income: `#1F6F43`
- Transfer: `#315D8C`
- Warning: `#8A5A00`

### Dark
- Primary: `#9AD5B2`
- Surface: `#101512`
- Surface elevated: `#18201B`
- Text primary: `#E6EEE8`
- Text secondary: `#B6C2BA`
- Border: `#354039`

Colour is never the only way to communicate transaction type or warning.

## Typography

Use Android system Roboto to avoid bundled-font size and licensing complexity.
- Display total: 32sp medium
- Screen title: 24sp medium
- Section title: 18sp medium
- Body: 16sp regular
- Secondary: 14sp regular
- Caption: 12sp regular
- Monetary values use tabular-number support where available

## Spacing and shape

- 4dp base grid
- Screen horizontal padding: 16dp
- Card radius: 16dp
- Input radius: 12dp
- Touch target minimum: 48×48dp

## Motion

- 150–250ms transitions
- No celebratory confetti for spending
- Respect reduced-motion system preference
- Balance changes animate only when the user caused the update

## Accessibility

- Text scalable to at least 200%
- Charts always have table equivalents
- Content descriptions for all icon-only actions
- Error text explains resolution
- Contrast target meets WCAG AA for normal text
