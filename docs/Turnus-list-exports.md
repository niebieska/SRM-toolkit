# Turnus list exports — Phase 3

Decisions recorded 2026-10-15. Implemented 2026-10-16 on
`feature/turnus-list-exports`.

Export controls are integrated into the list toolbar. Descriptive panel text is
omitted; loading, success and error feedback remain.

## Scope

Deliver XLSX, CSV and PDF from the existing Turnus lists. Reuse filtered people, selected columns and
sorting; participants and staff remain clearly distinguished. Staff roles/functions
remain included, and qualifications belong only to staff.

- XLSX: one workbook with separate Uczestnicy and Kadra sheets.
- CSV: one consistent table with a person-type column, participants before staff.
- PDF: agreed direct download, rather than browser print/save as the primary output.
- No Person register, stored export copies, Uwagi, Dopłata or payments in this phase.

## Agreed PDF decisions

The document has Turnus name/dates, list type, generation date, separate participant
and staff sections, repeated table headers and page numbers. Avoid splitting a
person's row between pages.

Use landscape pages for wide lists. If a custom column selection cannot fit
readably, ask the operator to select fewer columns. Do not silently omit columns
or reduce text to an unreadable size. Predefined lists have prepared layouts.

## Implementation

Files are generated locally from a snapshot of the loaded, filtered view taken
when Eksportuj is clicked. No additional registration request or external file
upload is made. Changing filters/columns/sorting is disabled while generating;
leaving the page or changing Turnus cancels the pending download. The export
count includes only matching people in groups with applicable selected columns.

- XLSX uses pinned write-excel-file 4.1.1, with separate sheets, frozen/styled
  headers, column widths and wrapped text. PESEL/phones are typed String cells;
  age is numeric. Even strings beginning with = are text, never formulas.
- CSV uses UTF-8 BOM, semicolon separators, CRLF rows and quoted/escaped fields.
  It forms one union of selected columns with Typ osoby. Missing staff-only cells
  are blank for participants. Formula-like values receive a protective apostrophe.
  Ordinary PESEL/numeric phone strings retain their original digits in the file.
  CSV cannot declare cell types: Excel may infer numbers and strip leading zeros
  when opening it. The UI recommends XLSX for typed identifiers in Excel.
- PDF uses pinned pdfmake 0.3.11 and its bundled Roboto fonts for Polish characters.
  A4 portrait/landscape is selected using per-column readability widths at 9pt.
  Excessive width or a row too tall to keep whole produces actionable feedback;
  no columns/text are silently truncated. Group and column headers repeat, with
  dontBreakRows and page numbering. Empty included groups have an explicit empty row.
- Both libraries load only when their format is requested. PDF engine/font chunks
  exceed Vite's 500 kB warning threshold (about 971/855 kB before gzip); they are
  separate lazy chunks, not initial page downloads. No warning threshold was hidden.
- File names use Turnus code, view and the generation date in Europe/Warsaw.
- No backend, database or existing registration API changes. No automatic commit
  or deployment is part of this implementation.

Library documentation: [write-excel-file](https://github.com/catamphetamine/write-excel-file),
[pdfmake client usage](https://pdfmake.github.io/docs/0.3/getting-started/client-side/).

Missing values stay blank. Export is disabled when there are no matching people
or no applicable columns. Repeated clicks cannot trigger duplicate generations.
Known PDF layout errors are shown in Polish; unexpected library errors receive a
generic retry message. Successful generation says the file was prepared, not that
it was saved to disk.

## Verification

Verify preset/custom columns, filters, sorting, separate groups, staff-only fields,
Polish characters, leading zeros, missing values and formula-like input. For PDF,
check multi-page headers, numbering, row breaks and the wide-selection message.
Verify generated files in actual spreadsheet/PDF applications before acceptance.

### Completed checks — 2026-10-16

- All 14 Biuro frontend test files and the production build pass; diff check clean.
- Tests cover export scope/snapshots, group-specific columns/counts, CSV escaping,
  formula-like input, typed workbook cells, PDF layout/repeated headers, wide/tall
  rejection, real binary generation for all formats, duplicate-click protection,
  empty selection and cancellation on Turnus change.
- Real XLSX ZIP/XML inspected: two correct sheet names, 140 participant/22 staff
  data rows, text identifiers with leading zeros, and zero formula nodes despite
  formula-like input. CSV parsed with consistent column counts and all 162 people.
- PDF artifacts generated for 162 synthetic people: portrait insurance (5 pages),
  landscape general (8 pages). Polish names, final rows, repeated headings and page
  numbers verified with Poppler text extraction; first page visually inspected.
- Headless Firefox exercised the actual export controls for XLSX, CSV and PDF.
  Captured all three blobs successfully; browser XLSX ZIP integrity, CSV identifiers
  and PDF Polish text verified. Mobile screenshot inspected at 390px.
- Dependency audit baseline and after installation both have 2 moderate/5 high
  findings in existing Tailwind-related dependencies; no new audit findings.
- Backend suite not rerun because no backend code changed. No lint/type-check
  scripts are defined. Temporary previews removed; synthetic artifacts stay in /tmp.

### Operator acceptance still needed

Open XLSX in the intended Excel/LibreOffice version, import CSV into the intended
recipient system, and review/print PDF with saved synthetic registrations. Check
filtered/custom lists, staff-only fields, empty groups and the PDF width message.
No real-person dataset or live backend end-to-end acceptance was used for these checks.
