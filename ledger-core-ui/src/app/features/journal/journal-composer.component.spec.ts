import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Account, JournalEntryResponse } from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';
import { JournalComposerComponent } from './journal-composer.component';

describe('JournalComposerComponent', () => {
  let api: {
    accounts: ReturnType<typeof signal<Account[]>>;
    periods: ReturnType<typeof signal<never[]>>;
    recentEntries: ReturnType<typeof signal<JournalEntryResponse[]>>;
    loadAccounts: ReturnType<typeof vi.fn>;
    loadPeriods: ReturnType<typeof vi.fn>;
    postJournalEntry: ReturnType<typeof vi.fn>;
  };
  let notifications: {
    error: ReturnType<typeof vi.fn>;
    success: ReturnType<typeof vi.fn>;
    fromHttpError: ReturnType<typeof vi.fn>;
  };

  const entry: JournalEntryResponse = {
    id: 'entry-1',
    entryNumber: 'JE-2026-000001',
    sourceType: 'MANUAL',
    status: 'POSTED',
    postingDate: '2026-11-20T00:00:00Z',
    transactionDate: '2026-11-20T00:00:00Z',
    description: 'Balanced entry',
    periodId: '2026-11',
    idempotencyKey: 'key-1',
    lines: [],
    totalAmount: '100.00',
    createdAt: '2026-11-20T00:00:00Z',
    createdBy: 'admin',
  };

  beforeEach(async () => {
    api = {
      accounts: signal<Account[]>([]),
      periods: signal<never[]>([]),
      recentEntries: signal<JournalEntryResponse[]>([]),
      loadAccounts: vi.fn().mockReturnValue(of([])),
      loadPeriods: vi.fn().mockReturnValue(of([])),
      postJournalEntry: vi.fn().mockReturnValue(of(entry)),
    };
    notifications = {
      error: vi.fn(),
      success: vi.fn(),
      fromHttpError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [JournalComposerComponent],
      providers: [
        { provide: LedgerApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
  });

  it('blocks an unbalanced journal entry', () => {
    const fixture = TestBed.createComponent(JournalComposerComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({ description: 'Unbalanced entry' });
    component.linesArray.at(0).patchValue({ accountId: 'cash', debitAmount: 100 });
    component.linesArray.at(1).patchValue({ accountId: 'revenue', creditAmount: 99 });

    component.onSubmit();

    expect(component.imbalance()).toBe(1);
    expect(component.isBalanced()).toBe(false);
    expect(api.postJournalEntry).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledOnce();
  });

  it('posts balanced lines and resets the composer after success', () => {
    const fixture = TestBed.createComponent(JournalComposerComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({ description: 'Balanced entry' });
    component.linesArray.at(0).patchValue({
      accountId: 'cash',
      debitAmount: 100,
      memo: 'Deposit',
      cashFlowCategory: 'OPERATING',
    });
    component.linesArray.at(1).patchValue({
      accountId: 'revenue',
      creditAmount: 100,
    });

    component.onSubmit();

    expect(api.postJournalEntry).toHaveBeenCalledWith(
      expect.objectContaining({
        description: 'Balanced entry',
        lines: [
          expect.objectContaining({
            accountId: 'cash',
            debitAmount: 100,
            creditAmount: 0,
            memo: 'Deposit',
            cashFlowCategory: 'OPERATING',
          }),
          expect.objectContaining({
            accountId: 'revenue',
            debitAmount: 0,
            creditAmount: 100,
          }),
        ],
      })
    );
    expect(component.totalDebits()).toBe(0);
    expect(component.totalCredits()).toBe(0);
    expect(component.submitting()).toBe(false);
    expect(notifications.success).toHaveBeenCalledOnce();
  });
});
