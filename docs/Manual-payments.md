# Wpłaty — probable MVP requirement

Updated 2026-11-02. The initial product scope is agreed, but inclusion in MVP is
still probable and has not been confirmed as a release gate. A frontend-only UI
prototype is being developed on [`feature/manual-payments-ui`](https://github.com/niebieska/SRM-toolkit/tree/feature/manual-payments-ui)
at commit `e7ab68a`; this branch is local and has not been pushed or merged into
`development`. The prototype uses in-memory demo data and does not persist payment,
discount, or Turnus fee changes. It is not an implementation of the payment feature.
Registration status history remains a confirmed MVP requirement.

## Agreed scope

- Add **Wpłaty** as a top-level Biuro tab alongside **Zgłoszenia**. It is one
  register for all Turnusy, with a Turnus filter; links from a Turnus may open the
  same register with that Turnus preselected.
- Add a fixed participant fee and fees for selected seasonal staff roles/subroles
  to the Turnus definition. Use the existing Summer/Winter role catalogue.
  Staff roles without an assigned fee pay 0 PLN. Staff fees depend on role, not age.
- The base fee for a participant or staff role/subrole is fixed in the Turnus
  definition and is not later edited. No per-registration fee snapshot is needed
  under this rule. If the amount due should be reduced later, grant an individual
  discount in PLN rather than changing the base fee. A discount may equal the full
  fee; percentage discounts may be considered later.
- Show accepted registrations with an applicable positive fee: participants and
  staff in roles assigned a fee. Do not show unpaid staff roles with no fee.
- OPERATOR finds a registration by registration code or surname and records its
  payments. The description choices are **Zaliczka**, **Dopłata**,
  **Wpłata całej kwoty**, or a custom description. Initial methods are
  **Przelew** and **Gotówka**.
- Record amount, payment date and optional note, with acting account and timestamp.
  Payment names classify entries; valid payments contribute to one paid total.
- Calculate the applicable fee, discount, total paid and balance. Balance is the
  fee minus discount minus valid payments; use decimal monetary calculations. The
  register shows discount, total paid, balance and status, without a separate fee
  column or summary tiles. Details show the status amount and individual payments.
- Payment status is **Brak wpłaty**, **Do dopłaty**, **Opłacone** or **Nadpłata**.
  Show the due amount for the first two and the excess amount for Nadpłata. Do not
  add a separate fee column or summary tiles to the main register/detail header.
  Lists may show payment status later, but do not allow payment edits there.
- In registration details show only the current discount amount, its description,
  the granting operator and date. Do not show a timeline of previous discount
  versions in this view; retain an audit trail of changes for financial traceability.
- Retain financial records after a registration status changes. Accepted-only
  eligibility governs adding payments, not deletion of their history.
- Correct a payment by editing the entry and saving an audit record with previous
  and new values, operator, timestamp and correction reason. Do not silently lose
  prior values.

## Expected implementation areas

- **Biuro DB/API:** Turnus fee configuration, role/subrole matching, operational
  permissions and aggregation of fees with registration financial records. The
  Turnus base fee belongs with the Biuro-owned Turnus definition.
- **registration-api/DB:** payment/discount records linked to registrations,
  migrations, protected endpoints and account attribution. Recommended ownership
  is `registration_db`, linked by registration ID; Biuro API would orchestrate the
  fee lookup and ledger calls. Confirm this boundary during implementation; no
  Person register is required.
- **Frontend:** Turnus fee controls and Wpłaty tab with entry/history/balance views.
- **Configuration:** predefined payment names and methods.
- **Tests:** role/subrole matching, monetary arithmetic, discounts/corrections,
  accepted-only writes, retained history, permissions and duplicate submissions.

## Decisions to confirm during implementation

- Confirm fee precedence when both a role and its subrole have fees; one applicable
  fee per registration is intended and fees are not added together.
- Confirm how to label a zero balance caused by a full discount when no payment was
  recorded: **Opłacone** or a separate fully discounted state. **Brak wpłaty: 0 zł**
  would be misleading.
- Confirm the recommended data boundary: payment/discount ledger in `registration_db`
  and Turnus fee configuration in `biuro_db`, with Biuro API orchestrating access.

These three points do not block starting implementation; resolve them with the
relevant domain and persistence details as they are built.

## Other exclusions and deferred decisions
- No correction of a staff role after payment is expected in the current workflow.
  Fees use the saved role/subrole; if role correction is later introduced, its
  effect on an existing fee/payment needs a separate decision.
- Remaining balances in Listy or a dedicated payment export are deferred.
- Percentage discounts, online payments, payment-provider integration, automatic
  reconciliation and reminders are outside the initial scope.
- No backend/API/DB implementation, delivery estimate or completed payment-feature
  acceptance is claimed by this note. The separate UI prototype has had a successful
  frontend build; its demo changes reset on refresh.
