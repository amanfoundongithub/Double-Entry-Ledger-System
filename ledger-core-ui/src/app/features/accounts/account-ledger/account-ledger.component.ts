import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  HostListener,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AccountLedgerEntryLine,
  AccountLedgerResponse,
} from '../../../core/models/ledger.models';
import { LedgerApiService } from '../../../core/services/ledger-api.service';
import { NotificationService } from '../../../core/services/notification.service';

export type LedgerSideFilter = 'ALL' | 'DEBIT' | 'CREDIT';

@Component({
  selector: 'app-account-ledger',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './account-ledger.component.html',
  styleUrl: './account-ledger.component.scss',
})
export class AccountLedgerComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(LedgerApiService);
  private readonly notifications = inject(NotificationService);

  readonly ledger = signal<AccountLedgerResponse | null>(null);
  readonly loading = signal<boolean>(false);

  readonly searchQuery = signal<string>('');
  readonly sideFilter = signal<LedgerSideFilter>('ALL');
  readonly selectedEntry = signal<AccountLedgerEntryLine | null>(null);

  accountId = '';
  startDate = '';
  endDate = '';

  readonly filteredEntries = computed(() => {
    const data = this.ledger();
    if (!data) {
      return [];
    }

    const q = this.searchQuery().trim().toLowerCase();
    const side = this.sideFilter();

    return data.entries.filter((row) => {
      const debit = this.toNumber(row.debitAmount);
      const credit = this.toNumber(row.creditAmount);

      if (side === 'DEBIT' && debit === 0) {
        return false;
      }
      if (side === 'CREDIT' && credit === 0) {
        return false;
      }

      if (!q) {
        return true;
      }

      const haystack = [
        row.entryNumber,
        row.reference ?? '',
        row.periodId,
        row.description,
        row.memo ?? '',
        row.journalEntryId,
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(q);
    });
  });

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.selectedEntry()) {
      this.closeInspector();
    }
  }

  ngOnInit(): void {
    const today = new Date();
    const firstOfMonth = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1));
    const lastOfMonth = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + 1, 0));

    const defaultStart = firstOfMonth.toISOString().slice(0, 10);
    const defaultEnd = lastOfMonth.toISOString().slice(0, 10);

    this.route.paramMap.subscribe((params) => {
      this.accountId = params.get('accountId') ?? '';

      const queryStart = this.route.snapshot.queryParamMap.get('startDate');
      const queryEnd = this.route.snapshot.queryParamMap.get('endDate');

      this.startDate = queryStart || defaultStart;
      this.endDate = queryEnd || defaultEnd;

      if (this.accountId) {
        this.loadLedger();
      }
    });
  }

  loadLedger(): void {
    if (!this.accountId) {
      return;
    }

    if (this.startDate && this.endDate && this.endDate < this.startDate) {
      this.notifications.error(
        'End date cannot be earlier than start date.',
        'Invalid Date Range'
      );
      return;
    }

    this.selectedEntry.set(null);
    this.loading.set(true);

    this.api.getAccountLedger(this.accountId, this.startDate, this.endDate).subscribe({
      next: (response) => {
        this.ledger.set(response);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.notifications.fromHttpError(err, 'Failed to Load Account Ledger');
      },
    });
  }

  openInspector(row: AccountLedgerEntryLine): void {
    this.selectedEntry.set(row);
  }

  closeInspector(): void {
    this.selectedEntry.set(null);
  }

  exportCsv(): void {
    const data = this.ledger();
    if (!data) {
      return;
    }

    const headers = [
      'Posting Date',
      'Transaction Date',
      'Entry Number',
      'Journal Entry ID',
      'Reference',
      'Status',
      'Period',
      'Description',
      'Memo',
      'Debit',
      'Credit',
      'Running Balance',
    ];

    const escapeCsv = (val: unknown): string => {
      const str = val === null || val === undefined ? '' : String(val);
      return `"${str.replace(/"/g, '""')}"`;
    };

    const rows: string[] = [
      headers.map(escapeCsv).join(','),
      [
        this.startDate,
        '',
        'OPENING',
        '',
        '',
        data.normalSide,
        '',
        `Opening Balance (${data.accountCode} - ${data.accountName})`,
        '',
        '',
        '',
        this.toNumber(data.openingBalance).toFixed(2),
      ]
        .map(escapeCsv)
        .join(','),
    ];

    for (const row of this.filteredEntries()) {
      rows.push(
        [
          row.postingDate.slice(0, 10),
          row.transactionDate ? row.transactionDate.slice(0, 10) : '',
          row.entryNumber,
          row.journalEntryId,
          row.reference ?? '',
          row.entryStatus,
          row.periodId,
          row.description,
          row.memo ?? '',
          this.toNumber(row.debitAmount).toFixed(2),
          this.toNumber(row.creditAmount).toFixed(2),
          this.toNumber(row.runningBalance).toFixed(2),
        ]
          .map(escapeCsv)
          .join(',')
      );
    }

    rows.push(
      [
        this.endDate,
        '',
        'CLOSING',
        '',
        '',
        data.normalSide,
        '',
        `Closing Balance (${data.accountCode} - ${data.accountName})`,
        '',
        this.toNumber(data.totalDebits).toFixed(2),
        this.toNumber(data.totalCredits).toFixed(2),
        this.toNumber(data.closingBalance).toFixed(2),
      ]
        .map(escapeCsv)
        .join(',')
    );

    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ledger-${data.accountCode}-${this.startDate}-to-${this.endDate}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    this.notifications.success(
      `Exported ${this.filteredEntries().length} ledger lines to CSV.`,
      'CSV Exported'
    );
  }

  toNumber(val: number | string | null | undefined): number {
    if (val === null || val === undefined || val === '') {
      return 0;
    }
    const parsed = typeof val === 'number' ? val : Number(val);
    return Number.isNaN(parsed) ? 0 : parsed;
  }
}