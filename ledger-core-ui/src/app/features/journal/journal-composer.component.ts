import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import {
  Account,
  CashFlowCategory,
  JournalEntryResponse,
  PostJournalEntryRequest,
  PostJournalLineRequest,
} from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'app-journal-composer',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './journal-composer.component.html',
  styleUrl: './journal-composer.component.scss',
})
export class JournalComposerComponent implements OnInit {
  private readonly api = inject(LedgerApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  readonly cashFlowCategories: CashFlowCategory[] = ['OPERATING', 'INVESTING', 'FINANCING', 'NONE'];

  readonly accounts = this.api.accounts;
  readonly recentEntries = this.api.recentEntries;
  readonly periods = this.api.periods;

  readonly submitting = signal<boolean>(false);
  readonly reversingId = signal<string | null>(null);

  readonly form = this.fb.group({
    reference: [''],
    sourceType: ['MANUAL'],
    postingDate: [new Date().toISOString().substring(0, 10), Validators.required],
    description: ['', [Validators.required, Validators.minLength(3)]],
    periodId: ['2026-11'],
    createdBy: ['admin'],
    lines: this.fb.array<FormGroup>([]),
  });

  readonly totalDebits = signal<number>(0);
  readonly totalCredits = signal<number>(0);

  readonly imbalance = computed(() => {
    const diff = Math.abs(this.totalDebits() - this.totalCredits());
    return Math.round(diff * 100) / 100;
  });

  readonly isBalanced = computed(() => {
    return this.imbalance() === 0 && this.totalDebits() > 0;
  });

  get linesArray(): FormArray<FormGroup> {
    return this.form.get('lines') as FormArray<FormGroup>;
  }

  ngOnInit(): void {
    if (this.accounts().length === 0) {
      this.api.loadAccounts().subscribe({
        error: (err: HttpErrorResponse) => this.notifications.fromHttpError(err, 'Failed to Load Accounts'),
      });
    }
    if (this.periods().length === 0) {
      this.api.loadPeriods().subscribe({
        error: (err: HttpErrorResponse) => this.notifications.fromHttpError(err, 'Failed to Load Periods'),
      });
    }

    this.addLine();
    this.addLine();

    this.form.valueChanges.subscribe(() => {
      this.recalculateTotals();
    });
  }

  createLineGroup(
    accountId = '',
    debit = 0,
    credit = 0,
    memo = '',
    cashFlow: CashFlowCategory = 'NONE'
  ): FormGroup {
    return this.fb.group({
      accountId: [accountId, Validators.required],
      debitAmount: [debit, [Validators.min(0)]],
      creditAmount: [credit, [Validators.min(0)]],
      memo: [memo],
      cashFlowCategory: [cashFlow],
    });
  }

  addLine(): void {
    this.linesArray.push(this.createLineGroup());
    this.recalculateTotals();
  }

  removeLine(index: number): void {
    if (this.linesArray.length > 2) {
      this.linesArray.removeAt(index);
      this.recalculateTotals();
    }
  }

  recalculateTotals(): void {
    let debits = 0;
    let credits = 0;

    for (const group of this.linesArray.controls) {
      const val = group.getRawValue();
      const d = Number(val['debitAmount']) || 0;
      const c = Number(val['creditAmount']) || 0;
      debits += d;
      credits += c;
    }

    this.totalDebits.set(Math.round(debits * 100) / 100);
    this.totalCredits.set(Math.round(credits * 100) / 100);
  }

  onAccountSelected(index: number): void {
    const lineGroup = this.linesArray.at(index);
    const accId = String(lineGroup.get('accountId')?.value ?? '');
    const account = this.accounts().find((a) => a.id === accId);
    if (account) {
      lineGroup.patchValue({
        cashFlowCategory: account.cashFlowCategory,
      });
    }
  }

  loadScenarioA(): void {
    const bank = this.findAccountByCode('1010');
    const ar = this.findAccountByCode('1200');
    const sales = this.findAccountByCode('4010');

    this.form.patchValue({
      reference: 'INV-2026-904',
      description: 'Partly cash (₹40,000) and partly credit (₹60,000) sale',
    });

    this.linesArray.clear();
    this.linesArray.push(this.createLineGroup(bank?.id || '', 40000, 0, '40% upfront cash', 'OPERATING'));
    this.linesArray.push(this.createLineGroup(ar?.id || '', 60000, 0, '60% credit term Net 30', 'NONE'));
    this.linesArray.push(this.createLineGroup(sales?.id || '', 0, 100000, 'Gross consulting revenue', 'NONE'));
    this.recalculateTotals();
  }

  loadScenarioB(): void {
    const bank = this.findAccountByCode('1010');
    const accumDeprec = this.findAccountByCode('1510');
    const equipment = this.findAccountByCode('1500');
    const gain = this.findAccountByCode('4900');

    this.form.patchValue({
      reference: 'DISP-2026-01',
      description: 'Disposal of equipment (₹150k cash, ₹80k accum depr, ₹200k asset, ₹30k gain)',
    });

    this.linesArray.clear();
    this.linesArray.push(this.createLineGroup(bank?.id || '', 150000, 0, 'Cash proceeds', 'INVESTING'));
    this.linesArray.push(this.createLineGroup(accumDeprec?.id || '', 80000, 0, 'Derecognize accum depreciation', 'NONE'));
    this.linesArray.push(this.createLineGroup(equipment?.id || '', 0, 200000, 'Remove gross cost', 'NONE'));
    this.linesArray.push(this.createLineGroup(gain?.id || '', 0, 30000, 'Gain on disposal', 'NONE'));
    this.recalculateTotals();
  }

  onSubmit(): void {
    if (this.form.invalid || !this.isBalanced() || this.linesArray.length < 2) {
      this.form.markAllAsTouched();
      this.notifications.error(
        'Ensure at least 2 lines, a valid description, and balanced debits and credits before posting.',
        'Cannot Post Entry'
      );
      return;
    }

    this.submitting.set(true);

    const raw = this.form.getRawValue();
    const linesPayload: PostJournalLineRequest[] = this.linesArray.controls.map((group) => {
      const val = group.getRawValue();
      return {
        accountId: String(val['accountId'] ?? ''),
        debitAmount: Number(val['debitAmount']) || 0,
        creditAmount: Number(val['creditAmount']) || 0,
        memo: val['memo'] ? String(val['memo']).trim() : undefined,
        cashFlowCategory: (val['cashFlowCategory'] as CashFlowCategory) || 'NONE',
      };
    });

    const payload: PostJournalEntryRequest = {
      reference: raw.reference?.trim() || undefined,
      sourceType: raw.sourceType || 'MANUAL',
      postingDate: raw.postingDate ? new Date(raw.postingDate).toISOString() : undefined,
      description: raw.description!.trim(),
      periodId: raw.periodId || undefined,
      createdBy: raw.createdBy || 'ui-user',
      lines: linesPayload,
    };

    this.api.postJournalEntry(payload).subscribe({
      next: (entry) => {
        this.submitting.set(false);
        this.notifications.success(
          `Successfully posted journal entry ${entry.entryNumber}`,
          'Journal Entry Posted'
        );
        this.resetForm();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.notifications.fromHttpError(err, 'Post Journal Entry Failed');
      },
    });
  }

  onReverse(entry: JournalEntryResponse): void {
    const reason = prompt(`Enter reason for reversing ${entry.entryNumber}:`, `Correction of ${entry.entryNumber}`);
    if (reason === null) {
      return;
    }

    this.reversingId.set(entry.id);

    this.api.reverseJournalEntry(entry.id, { reason: reason.trim(), createdBy: 'ui-admin' }).subscribe({
      next: (reversal) => {
        this.reversingId.set(null);
        this.notifications.success(
          `Reversal ${reversal.entryNumber} posted for original ${entry.entryNumber}`,
          'Entry Reversed'
        );
      },
      error: (err: HttpErrorResponse) => {
        this.reversingId.set(null);
        this.notifications.fromHttpError(err, 'Reversal Failed');
      },
    });
  }

  private resetForm(): void {
    this.form.patchValue({
      reference: '',
      description: '',
    });
    this.linesArray.clear();
    this.addLine();
    this.addLine();
    this.recalculateTotals();
  }

  private findAccountByCode(code: string): Account | undefined {
    return this.accounts().find((a) => a.code === code);
  }
}