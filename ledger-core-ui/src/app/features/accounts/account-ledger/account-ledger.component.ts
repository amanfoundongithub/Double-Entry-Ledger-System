import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AccountLedgerResponse } from '../../../core/models/ledger.models';
import { LedgerApiService } from '../../../core/services/ledger-api.service';
import { NotificationService } from '../../../core/services/notification.service';

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

  accountId = '';
  startDate = '';
  endDate = '';

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

  toNumber(val: number | string | null | undefined): number {
    if (val === null || val === undefined || val === '') {
      return 0;
    }
    const parsed = typeof val === 'number' ? val : Number(val);
    return Number.isNaN(parsed) ? 0 : parsed;
  }
}