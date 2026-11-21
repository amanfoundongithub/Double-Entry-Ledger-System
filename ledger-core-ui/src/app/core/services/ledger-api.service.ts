import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  Account,
  AccountLedgerResponse,
  BalanceSheetResponse,
  CashFlowStatementResponse,
  CreateAccountRequest,
  CreatePeriodRequest,
  CreateTemplateRequest,
  IncomeStatementResponse,
  JournalEntryResponse,
  PeriodResponse,
  PostJournalEntryRequest,
  ReverseJournalEntryRequest,
  TemplateResponse,
  TrialBalanceResponse,
} from '../models/ledger.models';

@Injectable({ providedIn: 'root' })
export class LedgerApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  readonly accounts = signal<Account[]>([]);
  readonly periods = signal<PeriodResponse[]>([]);
  readonly recentEntries = signal<JournalEntryResponse[]>([]);
  readonly templates = signal<TemplateResponse[]>([]);
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

  getJournalEntry(entryId: string): Observable<JournalEntryResponse> {
    return this.http.get<JournalEntryResponse>(`${this.baseUrl}/journal-entries/${entryId}`);
  }

  getJournalEntryByNumber(entryNumber: string): Observable<JournalEntryResponse> {
    return this.http.get<JournalEntryResponse>(`${this.baseUrl}/journal-entries/number/${encodeURIComponent(entryNumber)}`);
  }

  loadJournalEntries(limit = 25): Observable<JournalEntryResponse[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<JournalEntryResponse[]>(`${this.baseUrl}/journal-entries/recent`, { params }).pipe(
      tap((entries) => this.recentEntries.set(entries))
    );
  }

  reverseJournalEntry(
    entryId: string,
    request: ReverseJournalEntryRequest = {},
    idempotencyKey?: string
  ): Observable<JournalEntryResponse> {
    const key =
      idempotencyKey ||
      (typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `rev-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`);

    let params = new HttpParams();
    if (request.reason && request.reason.trim()) {
      params = params.set('reason', request.reason.trim());
    }
    if (request.createdBy && request.createdBy.trim()) {
      params = params.set('createdBy', request.createdBy.trim());
    }

    const headers = new HttpHeaders({
      'Idempotency-Key': key,
    });

    return this.http
      .post<JournalEntryResponse>(
        `${this.baseUrl}/journal-entries/${entryId}/reverse`,
        null,
        { headers, params }
      )
      .pipe(
        tap((reversalEntry) => {
          this.recentEntries.update((prev) => {
            const updatedExisting = prev.map((item) =>
              item.id === entryId || item.id === reversalEntry.reversalOfEntryId
                ? { ...item, status: 'REVERSED' as const, reversedByEntryId: reversalEntry.id }
                : item
            );
            const alreadyPresent = updatedExisting.some((item) => item.id === reversalEntry.id);
            return alreadyPresent ? updatedExisting : [reversalEntry, ...updatedExisting];
          });
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
    return this.http.get<AccountLedgerResponse>(`${this.baseUrl}/accounts/${accountId}/ledger`, { params });
  }

  loadTemplates(): Observable<TemplateResponse[]> {
    this.loading.set(true);
    return this.http.get<TemplateResponse[]>(`${this.baseUrl}/templates`).pipe(
      tap({
        next: (items) => {
          this.templates.set(items);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      })
    );
  }

  createTemplate(request: CreateTemplateRequest): Observable<TemplateResponse> {
    return this.http.post<TemplateResponse>(`${this.baseUrl}/templates`, request).pipe(
      tap((created) => this.templates.update((prev) => [...prev, created]))
    );
  }

  deleteTemplate(templateId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/templates/${encodeURIComponent(templateId)}`).pipe(
      tap(() => {
        this.templates.update((prev) => prev.filter((t) => t.id !== templateId));
      })
    );
  }

  advanceTemplateSchedule(templateId: string): Observable<TemplateResponse> {
  return this.http
    .post<TemplateResponse>(`${this.baseUrl}/templates/${encodeURIComponent(templateId)}/advance-schedule`, {})
    .pipe(
      tap((updated) => {
        this.templates.update((prev) =>
          prev.map((t) => (t.id === updated.id ? updated : t))
        );
      })
    );
}

  private syncPeriod(updated: PeriodResponse): void {
    this.periods.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
  }
}