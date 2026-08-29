# Cliniva — Design System & UX Direction

**Version:** 1.0
**Visual reference:** [ZeBeyond — Engineering Software Web App Design](https://dribbble.com/shots/27602379-Engineering-Software-Web-App-Design-ZeBeyond) by Phenomenon Studio (via Dribbble/Behance)

> **Note on the reference source:** The Dribbble shot renders client-side JavaScript and could not be loaded and visually inspected directly by the tooling used to write this document. What follows is built from (a) the shot's title, studio, and tag metadata, (b) the companion Behance case study description of the same project ("ZeBeyond — Engineering Software Platform Web Design," a B2B engineering/automotive software marketing site redesign by Phenomenon Studio, characterized as "modern, conversion-focused," speaking to "both decision-makers and technical users"), and (c) Phenomenon Studio's broader, consistently-documented visual language across their dashboard/SaaS portfolio (clean data-dense dashboards, restrained accent color, strong typographic hierarchy). Every specific value below (hex codes, exact spacing units, etc.) is a **design decision made for Cliniva**, informed by that general direction rather than pixel-measured from the source shot. Anywhere this matters, it is marked **[Assumption]**.

---

## 1. Visual Design

### 1.1 Overall Visual Style
Cliniva should read as **calm, precise, and trustworthy** — closer to a well-built engineering/ops tool than a consumer health app with soft illustration. The ZeBeyond reference is a B2B technical-software brand: clean grids, confident whitespace, restrained color, and typography doing most of the "personality" work rather than decoration. Cliniva adapts this for a healthcare-adjacent audience by warming the palette slightly and keeping status/urgency information (appointment states, payment states) highly legible, since clinic staff scan this UI quickly between patients.

**Design principles:**
- **Clarity over decoration.** A receptionist or doctor should be able to read a day's schedule at a glance.
- **Status is always visible.** Slot and appointment states (`AVAILABLE`, `BOOKED`, `PENDING_PAYMENT`, `CONFIRMED`, etc.) are core to the product and get consistent, unambiguous color coding everywhere they appear.
- **One accent color, used sparingly.** Following the reference's restrained-accent approach — primary actions and active/selected states use the accent; everything else stays neutral.
- **Technical, not clinical-sterile.** Avoid stock "healthcare app" clichés (soft pastel gradients, generic stethoscope icons). Favor the crisper, more structural feel of the engineering-software reference.

### 1.2 Layout Philosophy
- **Grid-based, 12-column responsive grid** at desktop widths, collapsing to a single column on mobile.
- **Content-first density**: dashboards and tables favor showing more real data over generous padding — but padding is used generously in negative space between *sections*, not stripped from every row (per the reference's dashboard work).
- **Left-anchored primary navigation** for authenticated app views (doctor/admin dashboards); patient-facing booking flow is more linear/wizard-like rather than nav-heavy, since patients complete a task (book a slot) rather than explore data.

### 1.3 Color System **[Assumption — specific hex values are Cliniva decisions]**

| Token | Light mode | Usage |
|---|---|---|
| `--color-bg` | `#F7F8FA` | App background |
| `--color-surface` | `#FFFFFF` | Cards, panels, tables |
| `--color-surface-raised` | `#FFFFFF` with `--shadow-sm` | Modals, popovers |
| `--color-border` | `#E4E7EC` | Hairline borders/dividers |
| `--color-text-primary` | `#0F1728` | Primary text |
| `--color-text-secondary` | `#5B6472` | Secondary/meta text |
| `--color-text-muted` | `#8A93A2` | Placeholder, disabled |
| `--color-accent` | `#2A5CE0` | Primary actions, links, active nav, focus rings |
| `--color-accent-hover` | `#2148B8` | Hover state of accent elements |

**Semantic / status colors** (used consistently for Slot, Appointment, Payment, Notification statuses):

| Status family | Color | Example values |
|---|---|---|
| Success / positive | `#1A9E6B` (green) | `CONFIRMED`, `COMPLETED`, `PAID`, `AVAILABLE`, `DELIVERED` |
| Warning / pending | `#C77B10` (amber) | `PENDING_PAYMENT`, `HELD`, `PENDING`, `CREATED`, `QUEUED` |
| Danger / negative | `#D23B3B` (red) | `CANCELLED`, `NO_SHOW`, `FAILED`, `BLOCKED` |
| Neutral / closed | `#6B7280` (gray) | `EXPIRED`, `BOOKED` (as "taken, not an error") |
| Info | `#2A5CE0` (accent blue) | informational badges, `SENT`, `PROCESSING` |

A **dark mode** variant should exist for the doctor/admin dashboard views specifically (clinic staff often use these during long shifts / low-light front-desk setups) — dark surfaces at `#12151C`/`#181C25`, following the "rich dark gray, never pure black" convention common to this class of dashboard product. Patient-facing booking screens can ship light-mode-only for MVP. **[Assumption: dark mode for staff views is a recommendation, not confirmed in the PRD — flag with the team.]**

### 1.4 Typography
- **Typeface:** a clean grotesk/geometric sans (e.g. Inter, or a comparable system font) for both UI chrome and data — matching the reference's technical, engineering-software feel. No serif, no display/decorative font.
- **Scale** (desktop):
  - Display / page title: 28–32px, semi-bold
  - Section heading: 20–22px, semi-bold
  - Card/table heading: 15–16px, semi-bold
  - Body: 14px, regular
  - Meta/caption: 12–13px, regular, `--color-text-secondary`
- Numeric data (times, prices, counts) uses **tabular figures** so schedule/table columns align cleanly — important for a slot grid or appointment table.

### 1.5 Spacing
- Base unit: **4px**. Spacing scale: 4, 8, 12, 16, 24, 32, 48, 64.
- Card internal padding: 16–24px. Section gaps: 32–48px. This generous macro-spacing paired with tighter micro-spacing inside data-dense components (tables, slot grids) mirrors the reference's "dense where it matters, airy everywhere else" approach.

### 1.6 Borders & Radius
- Hairline `1px` borders (`--color-border`) rather than heavy shadows to separate cards/sections — consistent with the flat, technical feel of the reference.
- Radius: `8px` for cards/inputs/buttons, `12px` for modals, `999px` (pill) for status badges.

### 1.7 Shadows
- Minimal. `--shadow-sm` (`0 1px 2px rgba(16,24,40,0.06)`) for resting cards; a slightly stronger `--shadow-md` only for popovers/modals/dropdowns that need to visually separate from content behind them. Avoid heavy drop shadows — flat + bordered is the default.

### 1.8 Iconography
- Line icons (1.5–2px stroke), not filled/glyph icons — again matching the precise, technical reference style. A single consistent icon set (e.g. an outline icon library) throughout.

### 1.9 Visual Hierarchy & Density
- Dashboard/table views (doctor schedule, admin doctor list) are **data-dense**: small type, tight row height, tabular alignment.
- Patient-facing booking screens are **lower density, higher whitespace**: one clear decision per screen (pick a doctor → pick a date → pick a slot → confirm).

### 1.10 Whitespace
- Used deliberately to separate *sections*, not to pad every element — whitespace is a hierarchy tool, not decoration.

---

## 2. Application Layout

### 2.1 Sidebar / Navigation (Doctor & Admin app shell)
- Fixed left sidebar (desktop): logo/clinic switcher at top, primary nav items below (Dashboard, Schedule/Appointments, Availability, Patients — admin only: Doctors, Clinic Settings), account/profile at the bottom.
- Collapses to an icon-only rail at tablet width, and to a bottom tab bar or slide-out drawer at mobile width.

### 2.2 Top Header
- Persistent top bar: page title/breadcrumb on the left, contextual actions (e.g. "Add Availability", date picker) and account menu on the right.
- Patient-facing flow uses a lighter top bar (logo + login/account) rather than the full app shell, since patients aren't navigating a dashboard.

### 2.3 Main Content Area
- Constrained max-width (~1280px) on large screens with centered content, avoiding stretched full-width tables/cards on ultra-wide monitors.

### 2.4 Dashboard (Doctor home / Admin home)
- Doctor dashboard: today's appointment list (time-ordered), quick stats (appointments today, pending payments), quick link to manage availability.
- Admin dashboard: clinic-wide doctor list with today's booking counts, quick add-doctor action.

### 2.5 Cards
- Used for: doctor profile summary, appointment summary, availability block summary. Standard card = surface + border + `8px` radius + `16–24px` padding, with a clear title row and optional status badge top-right.

### 2.6 Tables
- Used for: appointment lists, doctor lists (admin). Sticky header row, tabular-figure alignment for times/amounts, status column always rendered as a colored pill badge (see §1.3), row-level actions in a trailing column (icon buttons, not a wall of text buttons).

### 2.7 Forms
- Single-column forms for clarity (matches the reference's clean form patterns) — labels above inputs, not inline-left, for better mobile reflow. Inline validation messages appear directly beneath the field, in the danger color, tied to the `errors[]` array's `field`/`message` pairs from the API contract.

### 2.8 Detail Pages
- Doctor detail (patient-facing): profile header (name, specialization, fee, clinic block), then a slot-picker calendar/date-strip, then the slot grid for the selected date.
- Appointment detail: status badge prominent at top, then doctor/patient/date/time block, then action buttons (cancel/reschedule) gated by ownership and current status.

### 2.9 Settings
- Clinic settings (admin): clinic name/address/phone, doctor roster management.
- Account settings (all roles): profile info, password change, notification preferences (once channels exist).

### 2.10 Modals / Dialogs
- Used for confirmation flows only (cancel appointment, delete availability block) and for lightweight create forms (e.g. "Add availability") that don't need a full page. Modal max-width ~480–560px, `12px` radius, `--shadow-md`.

### 2.11 Notifications (in-app)
- Toast notifications (top-right, auto-dismiss ~4s) for action feedback ("Appointment booked", "Slot already taken — please choose another").
- A persistent notification bell (Phase 4, once the notification system exists) for reminders/system messages, separate from toasts.

### 2.12 Empty States
- Every list/table has a designed empty state: icon + short message + primary action where relevant (e.g. "No availability set yet" → "Add availability" button on the doctor's own schedule; "No slots available on this date" with a suggestion to pick another date, patient-facing).

### 2.13 Loading States
- Skeleton loaders (matching the shape of the eventual content — table rows, cards) rather than a generic spinner, for perceived-performance on data-heavy views (slot grid, appointment tables).
- A lightweight inline spinner is acceptable for button-level async actions (e.g. "Booking…" on the confirm button).

### 2.14 Error States
- Inline, field-level errors for validation (`422 VALIDATION_ERROR`).
- A distinct, non-blocking banner/toast for domain errors surfaced mid-flow (`409 SLOT_ALREADY_BOOKED` — "This slot was just booked by someone else. Please pick another time," with the slot grid auto-refreshing).
- A full-page error state (with retry) for `5xx`/network failures on primary data loads.

---

## 3. UX

### 3.1 Navigation Behavior
- Doctor/Admin: persistent sidebar navigation, deep-linkable routes for every major view (so a doctor can bookmark "today's schedule").
- Patient: linear booking flow (doctor → date → slot → confirm → pay) with a visible step indicator; back navigation always available without losing prior selections.

### 3.2 Responsive Behavior
- **Mobile-first for the patient booking flow** specifically — the PRD's persona set (patients booking on the go, doctors checking schedules "between patients" on a phone) makes this non-negotiable, not just a nice-to-have.
- Sidebar → bottom tab bar or hamburger drawer under ~768px.
- Tables degrade to stacked cards on mobile (one appointment = one card, key fields only, tap to expand).

### 3.3 User Interactions
- Primary actions (Book, Confirm, Save) are a single solid accent-colored button, always in the same position (bottom-right of forms/bottom-fixed on mobile).
- Destructive actions (Cancel appointment, Delete availability) are always confirmed via a modal, never a single click.

### 3.4 Feedback States
- Every async action shows: idle → loading (disabled button + inline spinner) → success (toast + UI update) or error (inline/toast per §2.14). No silent failures.

### 3.5 Form Behavior
- Validate on blur for individual fields, validate all on submit; submit button stays enabled but shows loading state on click (avoids "why can't I click submit" confusion) and reconciles with server-side `422` responses by mapping `errors[].field` back to the specific input.

### 3.6 Confirmation Flows
- Booking: a review step ("Dr. Sharma · Mon 20 Aug · 10:00–10:30 · ₹500") before final confirm, since booking triggers a payment obligation.
- Cancel/reschedule: modal confirmation with the reason field required for cancellation (matches the API contract's required `reason` field).

### 3.7 Accessibility Considerations
- Status must never be color-only: every status pill also carries a text label (e.g. "Confirmed", not just a green dot) — critical since color semantics (green/amber/red) must remain legible to color-blind users, especially for a healthcare-adjacent product.
- Minimum 4.5:1 text contrast against its surface at body-text sizes.
- All interactive elements keyboard-navigable with a visible focus ring (`--color-accent` outline).
- Form inputs always paired with a visible `<label>`, not placeholder-only labeling.

### 3.8 Mobile / Tablet / Desktop Behavior
- **Mobile (< 768px):** single column, bottom tab bar (staff) or step flow (patient), stacked cards instead of tables.
- **Tablet (768–1024px):** collapsed icon-rail sidebar, two-column card grids where space allows.
- **Desktop (> 1024px):** full sidebar, multi-column dashboards, tables with all columns visible.

---

## 4. Component Design

### 4.1 StatusBadge
- **Purpose:** consistent visual representation of any status enum (Slot, Appointment, Payment, Notification, AI job) across the whole product.
- **Variants:** `success`, `warning`, `danger`, `neutral`, `info` (mapped from the specific status value per §1.3's table).
- **States:** static (default) — no interactive state; may be used inside a button-like "filter chip" elsewhere, in which case it gains hover/selected states.
- **Props:** `status: string`, `variant` (derived automatically from a status→variant map, not chosen manually per-use, to guarantee consistency).
- **Behavior:** pill shape, colored background at ~10% opacity of the semantic color, colored text/dot at full opacity, always includes a text label.

### 4.2 SlotGrid
- **Purpose:** the core booking component — shows a doctor's slots for a selected date.
- **Variants:** patient-facing (selectable, `AVAILABLE` slots only are interactive) vs. doctor-facing (read-only, all statuses shown for the doctor's own review).
- **States:** loading (skeleton grid), empty ("No slots available"), populated, slot-selected (patient flow — highlighted with accent border), stale (a slot the user selected became `BOOKED` before submit — shown greyed with a "just booked" micro-label and auto-deselected).
- **Props:** `slots: Slot[]`, `selectedSlotId?`, `onSelect(slotId)`, `readOnly: boolean`.
- **Behavior:** groups slots visually by time-of-day (Morning/Afternoon/Evening) for scannability on a day with many slots.

### 4.3 AppointmentCard
- **Purpose:** compact summary of one appointment, used in lists (patient's "My Appointments", doctor's daily schedule) and as the mobile fallback for the AppointmentTable.
- **Variants:** patient view (shows doctor name/clinic), doctor view (shows patient name/phone).
- **States:** default, and a visually de-emphasized state for `CANCELLED`/`COMPLETED` (past/closed) appointments vs. an emphasized state for `CONFIRMED` upcoming ones.
- **Props:** `appointment`, `viewerRole: 'PATIENT' | 'DOCTOR'`, `onCancel?`, `onReschedule?`.
- **Behavior:** action buttons (cancel/reschedule) only render when the appointment status and viewer role make them valid actions (e.g. no cancel button on a `COMPLETED` appointment).

### 4.4 AvailabilityEditor
- **Purpose:** lets a doctor define/edit their recurring weekly schedule.
- **Variants:** none (single form), but each day-of-week row can be toggled active/inactive.
- **States:** empty (no availability set yet — see empty state §2.12), editing, saving.
- **Props:** `availabilityBlocks: DoctorAvailability[]`, `onSave`.
- **Behavior:** per-day rows with start time, end time, and slot duration; validates `startTime < endTime` client-side before submit, mirroring the same rule the backend enforces.

### 4.5 BookingStepper
- **Purpose:** the patient-facing linear flow shell (choose doctor → date → slot → confirm/pay).
- **Variants:** none; step count is fixed for MVP (payment step only shows once payments ship).
- **States:** each step can be `upcoming`, `active`, `complete`; back navigation preserves prior-step selections in memory.
- **Props:** `currentStep`, `steps[]`.
- **Behavior:** disallows advancing to the next step until the current step's required selection is made; on `SLOT_ALREADY_BOOKED` at the confirm step, returns the user to the slot-selection step with the grid refreshed.

### 4.6 ResponseToast
- **Purpose:** transient feedback for async actions (see §3.4).
- **Variants:** `success`, `error`, `info`.
- **States:** entering, visible, exiting (auto-dismiss ~4s, or manually dismissible).
- **Props:** `variant`, `message`, `onDismiss`.
- **Behavior:** stacks (max 3 visible) if multiple actions fire in quick succession; error toasts persist slightly longer (~6s) than success toasts.

### 4.7 DataTable
- **Purpose:** generic table shell for admin/doctor list views (doctors, appointments).
- **Variants:** with/without row-level actions column, with/without pagination footer.
- **States:** loading (skeleton rows), empty, populated, error (inline retry row).
- **Props:** `columns`, `rows`, `pagination`, `onPageChange`.
- **Behavior:** collapses to `AppointmentCard`-style stacked rows below the mobile breakpoint (§3.8).

---

## 5. Explicit Assumptions Summary

- Exact colors, spacing scale, radius values, and font choice are **Cliniva-specific decisions**, not measured from the ZeBeyond shot (which could not be rendered by the tooling used here). They follow the *general direction* implied by the reference and Phenomenon Studio's broader portfolio (clean B2B/engineering aesthetic, restrained accent color, data-dense but airy dashboards).
- Dark mode for staff-facing views is a recommendation based on typical usage patterns for this product category, not a confirmed requirement — flag with the team before committing engineering time to it.
- No component in this document reuses any proprietary asset, code, or exact visual detail from the Dribbble/Behance source; only the general visual language and interaction patterns are adapted.
