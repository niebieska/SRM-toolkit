# Wpłaty — probable MVP requirement

Updated 2026-11-06. The initial product scope is agreed, but inclusion in MVP is
still probable and has not been confirmed as a release gate. A payment UI
prototype is on the local [`feature/manual-payments-ui`](https://github.com/niebieska/SRM-toolkit/tree/feature/manual-payments-ui)
branch, which has no remote tracking branch. Turnus fee configuration is now
persisted by the Biuro API on this branch. The Wpłaty register still uses
in-memory demo registrations and does not persist payment or discount changes;
it is not an implementation of the payment ledger.
Registration status history remains a confirmed MVP requirement.

## Agreed scope

- Add **Wpłaty** as a top-level Biuro tab alongside **Zgłoszenia**. It is one
  register for all Turnusy, with a Turnus filter; links from a Turnus may open the
  same register with that Turnus preselected.
- Add a fixed participant fee and fees for selected seasonal staff roles/subroles
  to the Turnus definition. Use the existing Summer/Winter role catalogue.
  Staff roles without an assigned fee pay 0 PLN. Staff fees depend on role, not age.
  A role and its subrole will not have conflicting fee amounts; applicable amounts
  are not added together.
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
- Payment status is **Brak wpłaty**, **Do dopłaty**, **Opłacone**, **Nadpłata** or
  **Rozliczono rabatem**. Use **Rozliczono rabatem** when a full discount leaves
  zero due and no payment has been recorded. Show the due amount for the first two
  and the excess amount for Nadpłata. Do not add a separate fee column or summary
  tiles to the main register/detail header. Lists may show payment status later,
  but do not allow payment edits there.
- In registration details show only the current discount amount, its description,
  the granting operator and date. Do not show a timeline of previous discount
  versions in this view; retain an audit trail of changes for financial traceability.
- Retain financial records after a registration status changes. Accepted-only
  eligibility governs adding payments, not deletion of their history.
- Correct a payment by editing the entry and saving an audit record with previous
  and new values, operator, timestamp and correction reason. Do not silently lose
  prior values.

## Expected implementation areas

- **Biuro DB/API:** Turnus fee configuration and role/subrole validation are
  implemented on the current branch. Existing Turnus records can be configured
  once; later fee changes are rejected. The season is also locked after fees are
  configured because staff fee options depend on the seasonal catalogue. Fee
  aggregation with financial records and operational payment permissions remain.
  The Turnus base fee belongs with the Biuro-owned Turnus definition.
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

- Confirm the recommended data boundary: payment/discount ledger in `registration_db`
  and Turnus fee configuration in `biuro_db`, with Biuro API orchestrating access.

This architecture choice does not block starting implementation; confirm it with
the relevant persistence details as they are built.

## Other exclusions and deferred decisions
- No correction of a staff role after payment is expected in the current workflow.
  Fees use the saved role/subrole; if role correction is later introduced, its
  effect on an existing fee/payment needs a separate decision.
- Remaining balances in Listy or a dedicated payment export are deferred.
- Percentage discounts, online payments, payment-provider integration, automatic
  reconciliation and reminders are outside the initial scope.
- No payment-ledger backend/API/DB implementation, delivery estimate or completed
  payment-feature acceptance is claimed by this note. Turnus fee configuration is
  implemented on the current branch; the Wpłaty demo records still reset on refresh.
