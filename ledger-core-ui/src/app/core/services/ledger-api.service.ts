import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  Account,
  BalanceSheetResponse,
  CashFlowStatementResponse,
  CreateAccountRequest,
  CreatePeriodRequest,
  IncomeStatementResponse,
  JournalEntryResponse,
  PeriodResponse,
  PostJournalEntryRequest,
  ReverseJournalEntryRequest,
  TrialBalanceResponse,
  AccountLedgerResponse
} from '../models/ledger.models';

@Injectable({ providedIn: 'root' })
export class LedgerApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  readonly accounts = signal<Account[]>([]);
  readonly periods = signal<PeriodResponse[]>([]);
  readonly recentEntries = signal<JournalEntryResponse[]>([]);
  readonly loading = signal<boolean>(false);

  loadAccounts(): Observable<Account[]> {
    this.loading.set(true);
    return this.http.get<Account[]>(`${this.baseUrl}/accounts`).pipe(
      tap({
        next: (list) => {
          this.accounts.set(list);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      })
    );
  }

  createAccount(request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.baseUrl}/accounts`, request).pipe(
      tap((created) => this.accounts.update((prev) => [...prev, created]))
    );
  }

  postJournalEntry(request: PostJournalEntryRequest, idempotencyKey?: string): Observable<JournalEntryResponse> {
    const headers: Record<string, string> = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {};
    return this.http.post<JournalEntryResponse>(`${this.baseUrl}/journal-entries`, request, { headers }).pipe(
      tap((entry) => this.recentEntries.update((prev) => [entry, ...prev]))
    );
  }

  reverseJournalEntry(
    entryId: string,
    request: ReverseJournalEntryRequest = {},
    idempotencyKey?: string
  ): Observable<JournalEntryResponse> {
    const headers: Record<string, string> = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {};
    return this.http
      .post<JournalEntryResponse>(`${this.baseUrl}/journal-entries/${entryId}/reverse`, request, { headers })
      .pipe(
        tap(() => {
          this.recentEntries.update((prev) =>
            prev.map((item) => (item.id === entryId ? { ...item, status: 'REVERSED' } : item))
          );
        })
      );
  }

  loadPeriods(): Observable<PeriodResponse[]> {
    return this.http.get<PeriodResponse[]>(`${this.baseUrl}/periods`).pipe(
      tap((list) => this.periods.set(list))
    );
  }

  createPeriod(request: CreatePeriodRequest): Observable<PeriodResponse> {
    return this.http.post<PeriodResponse>(`${this.baseUrl}/periods`, request).pipe(
      tap((period) => this.periods.update((prev) => [...prev, period]))
    );
  }

  closePeriod(periodId: string, closedBy = 'ui-user'): Observable<PeriodResponse> {
    const params = new HttpParams().set('closedBy', closedBy);
    return this.http.patch<PeriodResponse>(`${this.baseUrl}/periods/${periodId}/close`, null, { params }).pipe(
      tap((updated) => this.syncPeriod(updated))
    );
  }

  lockPeriod(periodId: string, closedBy = 'ui-user'): Observable<PeriodResponse> {
    const params = new HttpParams().set('closedBy', closedBy);
    return this.http.patch<PeriodResponse>(`${this.baseUrl}/periods/${periodId}/lock`, null, { params }).pipe(
      tap((updated) => this.syncPeriod(updated))
    );
  }

  reopenPeriod(periodId: string): Observable<PeriodResponse> {
    return this.http.patch<PeriodResponse>(`${this.baseUrl}/periods/${periodId}/reopen`, null).pipe(
      tap((updated) => this.syncPeriod(updated))
    );
  }

  getTrialBalance(asOfDate: string): Observable<TrialBalanceResponse> {
    const params = new HttpParams().set('asOfDate', asOfDate);
    return this.http.get<TrialBalanceResponse>(`${this.baseUrl}/reports/trial-balance`, { params });
  }

  getIncomeStatement(startDate: string, endDate: string): Observable<IncomeStatementResponse> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<IncomeStatementResponse>(`${this.baseUrl}/reports/income-statement`, { params });
  }

  getBalanceSheet(asOfDate: string): Observable<BalanceSheetResponse> {
    const params = new HttpParams().set('asOfDate', asOfDate);
    return this.http.get<BalanceSheetResponse>(`${this.baseUrl}/reports/balance-sheet`, { params });
  }

  getCashFlowStatement(startDate: string, endDate: string): Observable<CashFlowStatementResponse> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<CashFlowStatementResponse>(`${this.baseUrl}/reports/cash-flow`, { params });
  }

  getAccountLedger(accountId: string, startDate: string, endDate: string): Observable<AccountLedgerResponse> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<AccountLedgerResponse>(`${this.baseUrl}/accounts/${accountId}/ledger`,{ params });
  }

  private syncPeriod(updated: PeriodResponse): void {
    this.periods.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
  }

}