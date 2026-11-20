import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  BalanceSheetResponse,
  CashFlowStatementResponse,
  IncomeStatementResponse,
  TrialBalanceResponse,
} from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

type StatementTab = 'TB' | 'PNL' | 'BS' | 'CF';

@Component({
  selector: 'app-statements',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  ngOnInit(): void {
    this.refreshActiveStatement();
  }

  selectTab(tab: StatementTab): void {
    this.activeTab.set(tab);
    this.refreshActiveStatement();
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
}