from __future__ import annotations

import calendar
import os
import uuid
from typing import Any

import httpx

BASE_URL = os.getenv("LEDGER_API_URL", "http://localhost:5555/api/v1")
NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
KEY_VERSION = "v2"


def deterministic_key(name: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{KEY_VERSION}:{name}"))


def period_bounds_iso(year: int, month: int) -> tuple[str, str]:
    last_day = calendar.monthrange(year, month)[1]
    start_iso = f"{year:04d}-{month:02d}-01T00:00:00Z"
    end_iso = f"{year:04d}-{month:02d}-{last_day:02d}T23:59:59.999Z"
    return start_iso, end_iso


def post_json(
    client: httpx.Client,
    path: str,
    payload: dict[str, Any] | None = None,
    idempotency_key: str | None = None,
    params: dict[str, Any] | None = None,
) -> dict[str, Any]:
    headers: dict[str, str] = {
        "X-Correlation-Id": str(uuid.uuid4()),
    }
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key

    resp = client.post(f"{BASE_URL}{path}", json=payload, headers=headers, params=params)
    if resp.status_code not in (200, 201):
        raise RuntimeError(f"POST {path} failed ({resp.status_code}): {resp.text}")
    return resp.json()


def seed() -> None:
    with httpx.Client(timeout=20.0) as client:
        # 1. Past Accounting Periods: 2026-01 through 2026-05
        month_names = {
            1: "January 2026",
            2: "February 2026",
            3: "March 2026",
            4: "April 2026",
            5: "May 2026",
        }

        periods = []
        for m in range(1, 6):
            pid = f"2026-{m:02d}"
            s_iso, e_iso = period_bounds_iso(2026, m)
            periods.append({
                "id": pid,
                "name": month_names[m],
                "startDate": s_iso,
                "endDate": e_iso,
            })

        for p in periods:
            resp = client.post(
                f"{BASE_URL}/periods",
                json=p,
                headers={
                    "X-Correlation-Id": str(uuid.uuid4()),
                    "Idempotency-Key": deterministic_key(f"period:{p['id']}"),
                },
            )
            if resp.status_code not in (200, 201, 409):
                print(f"Period {p['id']} note ({resp.status_code}): {resp.text}")

        # 2. Chart of Accounts (aligned with LedgerCore API collection)
        accounts_spec = [
            {
                "code": "1010",
                "name": "Operating Bank Account",
                "type": "ASSET",
                "normalSide": "DEBIT",
                "cashFlowCategory": "OPERATING",
                "cashEquivalent": True,
                "contra": False,
                "currency": "INR",
                "description": "Primary operating bank account for receipts and disbursements",
            },
            {
                "code": "1200",
                "name": "Accounts Receivable",
                "type": "ASSET",
                "normalSide": "DEBIT",
                "cashFlowCategory": "NONE",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Amounts owed by customers on credit invoices",
            },
            {
                "code": "1500",
                "name": "Office & Lab Equipment",
                "type": "ASSET",
                "normalSide": "DEBIT",
                "cashFlowCategory": "INVESTING",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Gross capitalized cost of equipment assets",
            },
            {
                "code": "1510",
                "name": "Accumulated Depreciation - Equipment",
                "type": "ASSET",
                "normalSide": "CREDIT",
                "cashFlowCategory": "NONE",
                "cashEquivalent": False,
                "contra": True,
                "currency": "INR",
                "description": "Contra-asset tracking cumulative depreciation on equipment",
            },
            {
                "code": "3010",
                "name": "Owner Capital",
                "type": "EQUITY",
                "normalSide": "CREDIT",
                "cashFlowCategory": "FINANCING",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Promoter / owner equity contributions",
            },
            {
                "code": "4010",
                "name": "Consulting & Ledger Revenue",
                "type": "REVENUE",
                "normalSide": "CREDIT",
                "cashFlowCategory": "NONE",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Earned service and consulting revenue",
            },
            {
                "code": "4900",
                "name": "Gain on Disposal of Equipment",
                "type": "REVENUE",
                "normalSide": "CREDIT",
                "cashFlowCategory": "NONE",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Net gain realized when disposing of fixed assets above carrying value",
            },
            {
                "code": "5010",
                "name": "Office Rent & Utilities",
                "type": "EXPENSE",
                "normalSide": "DEBIT",
                "cashFlowCategory": "NONE",
                "cashEquivalent": False,
                "contra": False,
                "currency": "INR",
                "description": "Monthly office lease and utility expenses",
            },
        ]

        accounts_by_code: dict[str, dict[str, Any]] = {}
        existing_resp = client.get(
            f"{BASE_URL}/accounts",
            headers={"X-Correlation-Id": str(uuid.uuid4())},
        )
        existing_resp.raise_for_status()
        for acc in existing_resp.json():
            accounts_by_code[acc["code"]] = acc

        for acc_payload in accounts_spec:
            code = acc_payload["code"]
            if code in accounts_by_code:
                continue
            created = post_json(
                client,
                "/accounts",
                payload=acc_payload,
                idempotency_key=deterministic_key(f"account:{code}"),
            )
            accounts_by_code[code] = created

        def acc_id(code: str) -> str:
            return accounts_by_code[code]["id"]

        # 3. Journal Entries: Jan to May 2026 (No entries in 2026-11 or 2026-13)
        entries_spec = [
            # --- JANUARY 2026 (Period 2026-01) ---
            {
                "key": "je-2026-01-001",
                "reference": "CAP-2026-JAN",
                "sourceType": "MANUAL",
                "postingDate": "2026-01-05T10:00:00Z",
                "transactionDate": "2026-01-05T10:00:00Z",
                "description": "Founding owner equity contribution",
                "periodId": "2026-01",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 600000,
                        "creditAmount": 0,
                        "memo": "Initial bank deposit",
                        "cashFlowCategory": "FINANCING",
                    },
                    {
                        "accountId": acc_id("3010"),
                        "debitAmount": 0,
                        "creditAmount": 600000,
                        "memo": "Promoter capital",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },
            {
                "key": "je-2026-01-002",
                "reference": "RENT-2026-01",
                "sourceType": "MANUAL",
                "postingDate": "2026-01-10T11:00:00Z",
                "transactionDate": "2026-01-10T11:00:00Z",
                "description": "January office lease & utilities",
                "periodId": "2026-01",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 30000,
                        "creditAmount": 0,
                        "memo": "January rent",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 30000,
                        "memo": "Paid via net banking",
                        "cashFlowCategory": "OPERATING",
                    },
                ],
            },
            {
                "key": "je-2026-01-003",
                "reference": "EQP-2026-01",
                "sourceType": "MANUAL",
                "postingDate": "2026-01-18T14:30:00Z",
                "transactionDate": "2026-01-18T14:30:00Z",
                "description": "Purchased server racks and developer workstations",
                "periodId": "2026-01",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1500"),
                        "debitAmount": 160000,
                        "creditAmount": 0,
                        "memo": "Hardware capitalized",
                        "cashFlowCategory": "INVESTING",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 160000,
                        "memo": "Vendor settlement",
                        "cashFlowCategory": "INVESTING",
                    },
                ],
            },
            {
                "key": "je-2026-01-004",
                "reference": "INV-2026-0101",
                "sourceType": "MANUAL",
                "postingDate": "2026-01-25T16:00:00Z",
                "transactionDate": "2026-01-25T16:00:00Z",
                "description": "January platform architecture consultation",
                "periodId": "2026-01",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 45000,
                        "creditAmount": 0,
                        "memo": "Immediate cash retainer",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("1200"),
                        "debitAmount": 55000,
                        "creditAmount": 0,
                        "memo": "Net 30 receivable",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("4010"),
                        "debitAmount": 0,
                        "creditAmount": 100000,
                        "memo": "Recognized consulting revenue",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },

            # --- FEBRUARY 2026 (Period 2026-02) ---
            {
                "key": "je-2026-02-001",
                "reference": "RCT-2026-0101",
                "sourceType": "MANUAL",
                "postingDate": "2026-02-06T10:15:00Z",
                "transactionDate": "2026-02-06T10:15:00Z",
                "description": "Collected January invoice balance",
                "periodId": "2026-02",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 55000,
                        "creditAmount": 0,
                        "memo": "Client payment cleared",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("1200"),
                        "debitAmount": 0,
                        "creditAmount": 55000,
                        "memo": "Cleared INV-2026-0101",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },
            {
                "key": "je-2026-02-002",
                "reference": "RENT-2026-02",
                "sourceType": "MANUAL",
                "postingDate": "2026-02-10T11:00:00Z",
                "transactionDate": "2026-02-10T11:00:00Z",
                "description": "February office rent & maintenance",
                "periodId": "2026-02",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 30000,
                        "creditAmount": 0,
                        "memo": "February workspace lease",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 30000,
                        "memo": "Outgoing transfer",
                        "cashFlowCategory": "OPERATING",
                    },
                ],
            },
            {
                "key": "je-2026-02-003",
                "reference": "INV-2026-0201",
                "sourceType": "MANUAL",
                "postingDate": "2026-02-20T14:45:00Z",
                "transactionDate": "2026-02-20T14:45:00Z",
                "description": "Full cash engagement for API integration",
                "periodId": "2026-02",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 80000,
                        "creditAmount": 0,
                        "memo": "Upfront settlement",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("4010"),
                        "debitAmount": 0,
                        "creditAmount": 80000,
                        "memo": "Integration consulting",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },

            # --- MARCH 2026 (Period 2026-03) ---
            {
                "key": "je-2026-03-001",
                "reference": "RENT-2026-03",
                "sourceType": "MANUAL",
                "postingDate": "2026-03-08T09:30:00Z",
                "transactionDate": "2026-03-08T09:30:00Z",
                "description": "March office rent",
                "periodId": "2026-03",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 32000,
                        "creditAmount": 0,
                        "memo": "March lease",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 32000,
                        "memo": "Paid from operating bank",
                        "cashFlowCategory": "OPERATING",
                    },
                ],
            },
            {
                "key": "je-2026-03-002",
                "reference": "INV-2026-0301",
                "sourceType": "MANUAL",
                "postingDate": "2026-03-15T15:00:00Z",
                "transactionDate": "2026-03-15T15:00:00Z",
                "description": "Enterprise double-entry ledger implementation",
                "periodId": "2026-03",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 60000,
                        "creditAmount": 0,
                        "memo": "First milestone",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("1200"),
                        "debitAmount": 90000,
                        "creditAmount": 0,
                        "memo": "Milestone due in April",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("4010"),
                        "debitAmount": 0,
                        "creditAmount": 150000,
                        "memo": "Contract revenue",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },
            {
                "key": "je-2026-03-003",
                "reference": "DEP-2026-Q1",
                "sourceType": "MANUAL",
                "postingDate": "2026-03-31T18:00:00Z",
                "transactionDate": "2026-03-31T18:00:00Z",
                "description": "Q1 equipment depreciation accrual",
                "periodId": "2026-03",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 16000,
                        "creditAmount": 0,
                        "memo": "Quarterly depreciation charge",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1510"),
                        "debitAmount": 0,
                        "creditAmount": 16000,
                        "memo": "Accumulated depreciation credit",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },

            # --- APRIL 2026 (Period 2026-04) ---
            {
                "key": "je-2026-04-001",
                "reference": "RCT-2026-0301",
                "sourceType": "MANUAL",
                "postingDate": "2026-04-10T11:20:00Z",
                "transactionDate": "2026-04-10T11:20:00Z",
                "description": "Collected March milestone receivable",
                "periodId": "2026-04",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 90000,
                        "creditAmount": 0,
                        "memo": "Client NEFT settlement",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("1200"),
                        "debitAmount": 0,
                        "creditAmount": 90000,
                        "memo": "Cleared INV-2026-0301",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },
            {
                "key": "je-2026-04-002",
                "reference": "RENT-2026-04",
                "sourceType": "MANUAL",
                "postingDate": "2026-04-12T10:00:00Z",
                "transactionDate": "2026-04-12T10:00:00Z",
                "description": "April office rent and internet services",
                "periodId": "2026-04",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 32000,
                        "creditAmount": 0,
                        "memo": "April rent & ISP",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 32000,
                        "memo": "Bank debit",
                        "cashFlowCategory": "OPERATING",
                    },
                ],
            },
            {
                "key": "je-2026-04-003",
                "reference": "DISP-2026-04",
                "sourceType": "MANUAL",
                "postingDate": "2026-04-22T13:40:00Z",
                "transactionDate": "2026-04-22T13:40:00Z",
                "description": "Sold excess testing hardware above book value",
                "periodId": "2026-04",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 35000,
                        "creditAmount": 0,
                        "memo": "Cash sale proceeds",
                        "cashFlowCategory": "INVESTING",
                    },
                    {
                        "accountId": acc_id("1500"),
                        "debitAmount": 0,
                        "creditAmount": 25000,
                        "memo": "Original equipment derecognized",
                        "cashFlowCategory": "INVESTING",
                    },
                    {
                        "accountId": acc_id("4900"),
                        "debitAmount": 0,
                        "creditAmount": 10000,
                        "memo": "Gain on disposal",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },

            # --- MAY 2026 (Period 2026-05) ---
            {
                "key": "je-2026-05-001",
                "reference": "RENT-2026-05",
                "sourceType": "MANUAL",
                "postingDate": "2026-05-08T10:00:00Z",
                "transactionDate": "2026-05-08T10:00:00Z",
                "description": "May office rent and power bills",
                "periodId": "2026-05",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("5010"),
                        "debitAmount": 33000,
                        "creditAmount": 0,
                        "memo": "May facility costs",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 0,
                        "creditAmount": 33000,
                        "memo": "Transfer via operating account",
                        "cashFlowCategory": "OPERATING",
                    },
                ],
            },
            {
                "key": "je-2026-05-002",
                "reference": "INV-2026-0501",
                "sourceType": "MANUAL",
                "postingDate": "2026-05-15T15:30:00Z",
                "transactionDate": "2026-05-15T15:30:00Z",
                "description": "May advisory retainer invoice",
                "periodId": "2026-05",
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 40000,
                        "creditAmount": 0,
                        "memo": "Bank retainer",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("1200"),
                        "debitAmount": 60000,
                        "creditAmount": 0,
                        "memo": "Pending receivable",
                        "cashFlowCategory": "NONE",
                    },
                    {
                        "accountId": acc_id("4010"),
                        "debitAmount": 0,
                        "creditAmount": 100000,
                        "memo": "Advisory revenue",
                        "cashFlowCategory": "NONE",
                    },
                ],
            },
        ]

        for spec in entries_spec:
            key = deterministic_key(f"je:{spec['key']}")
            body = {k: v for k, v in spec.items() if k != "key"}
            post_json(client, "/journal-entries", payload=body, idempotency_key=key)

        # 4. Seed a reversal only if an OPEN period covers today (2026-11-21)
        periods_resp = client.get(
            f"{BASE_URL}/periods",
            headers={"X-Correlation-Id": str(uuid.uuid4())},
        )
        periods_resp.raise_for_status()

        open_current_period = next(
            (
                p
                for p in periods_resp.json()
                if p.get("status") == "OPEN"
                and str(p.get("startDate", "")) <= "2026-11-21T23:59:59.999Z"
                and str(p.get("endDate", "")) >= "2026-11-21T00:00:00Z"
            ),
            None,
        )

        if open_current_period:
            rev_target_spec = {
                "reference": "INV-2026-REV-TEST",
                "sourceType": "MANUAL",
                "postingDate": "2026-11-21T06:00:00Z",
                "transactionDate": "2026-11-21T06:00:00Z",
                "description": "Entry in open period for automated reversal seed",
                "periodId": open_current_period["id"],
                "createdBy": "seed-script",
                "lines": [
                    {
                        "accountId": acc_id("1010"),
                        "debitAmount": 25000,
                        "creditAmount": 0,
                        "memo": "Temporary deposit",
                        "cashFlowCategory": "OPERATING",
                    },
                    {
                        "accountId": acc_id("4010"),
                        "debitAmount": 0,
                        "creditAmount": 25000,
                        "memo": "Temporary revenue",
                        "cashFlowCategory": "NONE",
                    },
                ],
            }
            created_target = post_json(
                client,
                "/journal-entries",
                payload=rev_target_spec,
                idempotency_key=deterministic_key("je:rev-target-current-period"),
            )
            if not created_target.get("reversedByEntryId"):
                post_json(
                    client,
                    f"/journal-entries/{created_target['id']}/reverse",
                    idempotency_key=deterministic_key(f"rev:{created_target['id']}"),
                    params={
                        "reason": "Seeded reversal in open period",
                        "createdBy": "seed-script",
                    },
                )
        else:
            print("Skipping reversal seed: no OPEN period covers 2026-11-21.")

        print(f"Seeding complete ({len(entries_spec)} entries seeded across 2026-01..2026-05).")


if __name__ == "__main__":
    seed()