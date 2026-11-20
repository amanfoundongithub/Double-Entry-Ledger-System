import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LedgerApiService } from './ledger-api.service';
import { Account, JournalEntryResponse, PeriodResponse } from '../models/ledger.models';

describe('LedgerApiService', () => {
  let service: LedgerApiService;
  let http: HttpTestingController;

  const account: Account = {
    id: 'account-1',
    code: '1010',
    name: 'Operating bank',
    type: 'ASSET',
    normalSide: 'DEBIT',
    cashFlowCategory: 'OPERATING',
    currency: 'INR',
    active: true,
  };

  const period: PeriodResponse = {
    id: '2026-11',
    name: 'November 2026',
    startDate: '2026-11-01T00:00:00Z',
    endDate: '2026-11-30T23:59:59.999Z',
    status: 'OPEN',
  };

  const entry: JournalEntryResponse = {
    id: 'entry-1',
    entryNumber: 'JE-2026-000001',
    sourceType: 'MANUAL',
    status: 'POSTED',
    postingDate: '2026-11-20T00:00:00Z',
    transactionDate: '2026-11-20T00:00:00Z',
    description: 'Opening entry',
    periodId: '2026-11',
    idempotencyKey: 'key-1',
    lines: [],
    totalAmount: '100.00',
    createdAt: '2026-11-20T00:00:00Z',
    createdBy: 'test',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        LedgerApiService,
      ],
    });
    service = TestBed.inject(LedgerApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('updates account state and loading while accounts are loaded', () => {
    let response: Account[] | undefined;
    service.loadAccounts().subscribe((accounts) => (response = accounts));

    expect(service.loading()).toBe(true);
    const request = http.expectOne('/api/v1/accounts');
    expect(request.request.method).toBe('GET');
    request.flush([account]);

    expect(response).toEqual([account]);
    expect(service.accounts()).toEqual([account]);
    expect(service.loading()).toBe(false);
  });

  it('clears loading when loading accounts fails', () => {
    service.loadAccounts().subscribe({ error: () => undefined });

    http.expectOne('/api/v1/accounts').flush('Unavailable', {
      status: 503,
      statusText: 'Service Unavailable',
    });

    expect(service.loading()).toBe(false);
  });

  it('adds newly created accounts to its state', () => {
    service.accounts.set([]);
    service.createAccount({
      code: account.code,
      name: account.name,
      type: account.type,
      normalSide: account.normalSide,
      cashFlowCategory: account.cashFlowCategory,
      currency: account.currency,
    }).subscribe();

    const request = http.expectOne('/api/v1/accounts');
    expect(request.request.method).toBe('POST');
    request.flush(account);

    expect(service.accounts()).toEqual([account]);
  });

  it('sends idempotency keys and tracks new journal entries first', () => {
    service.postJournalEntry(
      { description: 'Opening entry', lines: [] },
      'journal-key'
    ).subscribe();

    const request = http.expectOne('/api/v1/journal-entries');
    expect(request.request.headers.get('Idempotency-Key')).toBe('journal-key');
    request.flush(entry);

    expect(service.recentEntries()).toEqual([entry]);
  });

  it('syncs period status after a close request', () => {
    const closedPeriod = { ...period, status: 'CLOSED' as const };
    service.periods.set([period]);
    service.closePeriod(period.id).subscribe();

    const request = http.expectOne(
      (req) => req.url === `/api/v1/periods/${period.id}/close`
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.params.get('closedBy')).toBe('ui-user');
    request.flush(closedPeriod);

    expect(service.periods()).toEqual([closedPeriod]);
  });
});
