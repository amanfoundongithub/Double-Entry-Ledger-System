import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AccountType,
  CashFlowCategory,
  CreateAccountRequest,
  NormalSide,
} from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './accounts.component.html',
  styleUrl: './accounts.component.scss',
})
export class AccountsComponent implements OnInit {
  private readonly api = inject(LedgerApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  readonly accountTypes: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'];
  readonly cashFlowCategories: CashFlowCategory[] = ['OPERATING', 'INVESTING', 'FINANCING', 'NONE'];

  readonly selectedType = signal<AccountType | 'ALL'>('ALL');
  readonly submitting = signal<boolean>(false);

  readonly accounts = this.api.accounts;
  readonly loading = this.api.loading;

  readonly filteredAccounts = computed(() => {
    const filter = this.selectedType();
    const list = this.accounts();
    if (filter === 'ALL') {
      return list;
    }
    return list.filter((a) => a.type === filter);
  });

  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    type: ['ASSET' as AccountType, Validators.required],
    normalSide: ['DEBIT' as NormalSide, Validators.required],
    cashFlowCategory: ['NONE' as CashFlowCategory, Validators.required],
    cashEquivalent: [false],
    contra: [false],
    currency: ['INR', [Validators.required, Validators.minLength(3), Validators.maxLength(3)]],
    description: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.form.controls.type.valueChanges.subscribe((type) => {
      const suggested: NormalSide =
        type === 'ASSET' || type === 'EXPENSE' ? 'DEBIT' : 'CREDIT';
      this.form.controls.normalSide.setValue(suggested);
    });

    this.api.loadAccounts().subscribe({
      error: (err: HttpErrorResponse) =>
        this.notifications.fromHttpError(err, 'Failed to Load Accounts'),
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifications.error(
        'Please enter a valid account code, name, and 3-letter currency.',
        'Validation Failed'
      );
      return;
    }

    this.submitting.set(true);

    const raw = this.form.getRawValue();
    const payload: CreateAccountRequest = {
      code: raw.code.trim(),
      name: raw.name.trim(),
      type: raw.type,
      normalSide: raw.normalSide,
      cashFlowCategory: raw.cashFlowCategory,
      cashEquivalent: raw.cashEquivalent,
      contra: raw.contra,
      currency: raw.currency.trim().toUpperCase(),
      description: raw.description.trim() ? raw.description.trim() : null,
    };

    this.api.createAccount(payload).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.notifications.success(
          `Created account ${created.code} — ${created.name}`,
          'Account Created'
        );
        this.form.patchValue({ code: '', name: '', description: '' });
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.notifications.fromHttpError(err, 'Create Account Failed');
      },
    });
  }
}