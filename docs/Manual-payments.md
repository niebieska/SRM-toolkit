# Wpłaty — probable MVP requirement

Discussed 2026-10-20. Status: agreed initial scope, not implemented; inclusion in
MVP is probable and still subject to prioritization. This is not yet a firm release
gate. Registration status history remains a confirmed MVP requirement.

## Agreed scope

- Add **Wpłaty** in Turnus details next to **Listy**.
- Add a fixed participant fee and fees for selected seasonal staff roles/subroles
  to the Turnus definition. Use the existing Summer/Winter role catalogue.
  Staff roles without an assigned fee pay 0 PLN. Staff fees depend on role, not age.
- Turnus fees stay fixed; no per-registration fee snapshot is planned under this
  assumption. Individual discounts are amounts in PLN initially; percentages later.
- OPERATOR manages payments for accepted registrations, using predefined payment
  names (e.g. Zaliczka, Dopłata, Pełna wpłata) and methods (e.g. Przelew, Gotówka).
  These examples are not a finalized configuration catalogue.
- Record amount, payment date and optional note, with acting account and timestamp.
  Payment names classify entries; valid payments contribute to one paid total.
- Show fee, discount, total paid, balance and payment history. Balance is applicable
  fee minus discount minus valid payments. Use decimal monetary calculations.
- Retain financial records after a registration status changes. Accepted-only
  eligibility governs adding payments, not deletion of their history.
- Preserve an audit trail for discount changes and payment corrections. Proposed
  correction approach: cancel an entry with a reason, then add a replacement;
  do not silently overwrite/delete financial history.

## Expected implementation areas

- **Biuro DB/API:** Turnus fee configuration, role/subrole matching, operational
  permissions and aggregation of fees with registration financial records.
- **registration-api/DB:** proposed ownership of payment/discount records linked
  to registrations, migrations, protected endpoints and account attribution.
  Confirm ownership before implementation; no Person register is required.
- **Frontend:** Turnus fee controls and Wpłaty tab with entry/history/balance views.
- **Configuration:** predefined payment names and methods.
- **Tests:** role/subrole matching, monetary arithmetic, discounts/corrections,
  accepted-only writes, retained history, permissions and duplicate submissions.

## Open decisions and exclusions

- Define precedence when both a role and its subrole have fees. One applicable
  fee per registration is intended; fees are not added together.
- Confirm exact payment names/methods, fee initialization/locking and applicability
  if registration role data is corrected later.
- Confirm discount bounds, cancellation permissions and overpayment behavior;
  displaying Nadpłata was proposed but not explicitly accepted.
- Remaining balances in Listy or a dedicated payment export are deferred.
- Percentage discounts, online payments, payment-provider integration, automatic
  reconciliation and reminders are outside the initial scope.
- No implementation, estimate or completed acceptance is claimed by this note.
