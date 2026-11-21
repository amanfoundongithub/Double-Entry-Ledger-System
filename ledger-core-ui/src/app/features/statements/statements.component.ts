import { CommonModule, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  BalanceSheetResponse,
  CashFlowStatementResponse,
  IncomeStatementResponse,
  StatementAccountLine,
  TrialBalanceResponse,
} from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

type StatementTab = 'TB' | 'PNL' | 'BS' | 'CF';

@Component({
  selector: 'app-statements',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DecimalPipe],
  templateUrl: './statements.component.html',
  styleUrl: './statements.component.scss',
})
export class StatementsComponent implements OnInit {
  private readonly api = inject(LedgerApiService);
  private readonly notifications = inject(NotificationService);

  activeTab = signal<StatementTab>('TB');
  asOfDate = '2026-11-30';
  startDate = '2026-11-01';
  endDate = '2026-11-30';

  readonly loading = signal<boolean>(false);

  readonly trialBalance = signal<TrialBalanceResponse | null>(null);
  readonly incomeStatement = signal<IncomeStatementResponse | null>(null);
  readonly balanceSheet = signal<BalanceSheetResponse | null>(null);
  readonly cashFlow = signal<CashFlowStatementResponse | null>(null);

  private readonly contraAccountIds = computed(() => {
    const ids = new Set<string>();
    for (const acc of this.api.accounts()) {
      if (acc.contra) {
        ids.add(acc.id);
      }
    }
    return ids;
  });

  ngOnInit(): void {
    if (this.api.accounts().length === 0) {
      this.api.loadAccounts().subscribe();
    }
    this.refreshActiveStatement();
  }

  selectTab(tab: StatementTab): void {
    this.activeTab.set(tab);
    this.refreshActiveStatement();
  }

  ledgerQueryParams(): { startDate?: string; endDate: string } {
    const tab = this.activeTab();
    if (tab === 'PNL' || tab === 'CF') {
      return {
        startDate: this.startDate,
        endDate: this.endDate,
      };
    }
    return {
      endDate: this.asOfDate,
    };
  }

  refreshActiveStatement(): void {
    this.loading.set(true);

    const asOfIso = new Date(`${this.asOfDate}T23:59:59.999Z`).toISOString();
    const startIso = new Date(`${this.startDate}T00:00:00.000Z`).toISOString();
    const endIso = new Date(`${this.endDate}T23:59:59.999Z`).toISOString();

    const onError = (err: HttpErrorResponse) => {
      this.loading.set(false);
      this.notifications.fromHttpError(err, 'Statement Error');
    };

    switch (this.activeTab()) {
      case 'TB':
        this.api.getTrialBalance(asOfIso).subscribe({
          next: (res) => {
            this.trialBalance.set(res);
            this.loading.set(false);
          },
          error: onError,
        });
        break;
      case 'PNL':
        this.api.getIncomeStatement(startIso, endIso).subscribe({
          next: (res) => {
            this.incomeStatement.set(res);
            this.loading.set(false);
          },
          error: onError,
        });
        break;
      case 'BS':
        this.api.getBalanceSheet(asOfIso).subscribe({
          next: (res) => {
            this.balanceSheet.set(res);
            this.loading.set(false);
          },
          error: onError,
        });
        break;
      case 'CF':
        this.api.getCashFlowStatement(startIso, endIso).subscribe({
          next: (res) => {
            this.cashFlow.set(res);
            this.loading.set(false);
          },
          error: onError,
        });
        break;
    }
  }

  isContra(
    line: ({ accountId?: string; contra?: boolean; amount?: number | string } & Partial<StatementAccountLine>) | null | undefined
  ): boolean {
    if (!line) {
      return false;
    }
    if (line.contra) {
      return true;
    }
    if (line.accountId && this.contraAccountIds().has(line.accountId)) {
      return true;
    }
    return this.toNumber(line.amount) < 0;
  }

  toNumber(val: number | string | null | undefined): number {
    if (val === null || val === undefined || val === '') {
      return 0;
    }
    const parsed = typeof val === 'number' ? val : Number(val);
    return Number.isNaN(parsed) ? 0 : parsed;
  }

  /**
   * Returns standard financial format: absolute value in parentheses if contra or negative
   */
  formatFinancial(val: number | string | null | undefined, isContra = false): string {
    const num = this.toNumber(val);
    const absVal = Math.abs(num).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    if (isContra || num < 0) {
      return `(${absVal})`;
    }
    return absVal;
  }
}