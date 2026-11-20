import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Account } from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';
import { AccountsComponent } from './accounts.component';

describe('AccountsComponent', () => {
  let api: {
    accounts: ReturnType<typeof signal<Account[]>>;
    loading: ReturnType<typeof signal<boolean>>;
    loadAccounts: ReturnType<typeof vi.fn>;
    createAccount: ReturnType<typeof vi.fn>;
  };
  let notifications: {
    error: ReturnType<typeof vi.fn>;
    success: ReturnType<typeof vi.fn>;
    fromHttpError: ReturnType<typeof vi.fn>;
  };

  const createdAccount: Account = {
    id: 'account-1',
    code: '1010',
    name: 'Operating bank',
    type: 'ASSET',
    normalSide: 'DEBIT',
    cashFlowCategory: 'OPERATING',
    currency: 'INR',
    active: true,
  };

  beforeEach(async () => {
    api = {
      accounts: signal<Account[]>([]),
      loading: signal(false),
      loadAccounts: vi.fn().mockReturnValue(of([])),
      createAccount: vi.fn().mockReturnValue(of(createdAccount)),
    };
    notifications = {
      error: vi.fn(),
      success: vi.fn(),
      fromHttpError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [
        { provide: LedgerApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
  });

  it('loads accounts and filters them by account type', () => {
    const fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();
    api.accounts.set([
      createdAccount,
      { ...createdAccount, id: 'account-2', code: '4010', type: 'REVENUE' },
    ]);

    expect(api.loadAccounts).toHaveBeenCalledOnce();
    expect(fixture.componentInstance.filteredAccounts()).toHaveLength(2);

    fixture.componentInstance.selectedType.set('REVENUE');
    expect(fixture.componentInstance.filteredAccounts().map((item) => item.code)).toEqual([
      '4010',
    ]);
  });

  it('normalizes account fields before creating and resets the form on success', () => {
    const fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.setValue({
      code: ' 1010 ',
      name: ' Operating bank ',
      type: 'ASSET',
      normalSide: 'DEBIT',
      cashFlowCategory: 'OPERATING',
      cashEquivalent: true,
      contra: false,
      currency: 'inr',
      description: ' Main operating account ',
    });

    component.onSubmit();

    expect(api.createAccount).toHaveBeenCalledWith({
      code: '1010',
      name: 'Operating bank',
      type: 'ASSET',
      normalSide: 'DEBIT',
      cashFlowCategory: 'OPERATING',
      cashEquivalent: true,
      contra: false,
      currency: 'INR',
      description: 'Main operating account',
    });
    expect(component.submitting()).toBe(false);
    expect(component.form.controls.code.value).toBe('');
    expect(notifications.success).toHaveBeenCalledOnce();
  });

  it('does not call the API when the account form is invalid', () => {
    const fixture = TestBed.createComponent(AccountsComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.patchValue({ code: '', name: '' });
    fixture.componentInstance.onSubmit();

    expect(api.createAccount).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith(
      'Please enter a valid account code, name, and 3-letter currency.',
      'Validation Failed'
    );
  });
});
