import { CommonModule, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CashFlowCategory,
  CreateTemplateRequest,
  JournalEntryResponse,
  LedgerImpactRow,
  NormalSide,
  PeriodResponse,
  PostJournalEntryRequest,
  PostJournalLineRequest,
  TemplateAllocationType,
  TemplateCategory,
  TemplateLineDef,
  TemplateResponse,
} from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

interface DraftTemplateLine {
  accountCode: string;
  accountName: string;
  side: NormalSide;
  cashFlowCategory: CashFlowCategory;
  memo: string;
  amount: number;
  amountType: TemplateAllocationType;
  allocationPercent: number | null;
  balancingLeg: boolean;
}

interface NewTemplateLineForm {
  accountCode: string;
  accountName: string;
  side: NormalSide;
  cashFlowCategory: CashFlowCategory;
  defaultMemo: string;
  defaultAmount: number | null;
  amountType: TemplateAllocationType;
  allocationPercent: number | null;
}

@Component({
  selector: 'app-template-poster',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
  templateUrl: './template-poster.component.html',
  styleUrl: './template-poster.component.scss',
})
export class TemplatePosterComponent implements OnInit {
  private readonly api = inject(LedgerApiService);
  private readonly notifications = inject(NotificationService);

  readonly pageSize = 4;

  readonly categories: ('ALL' | TemplateCategory)[] = [
    'ALL',
    'REVENUE',
    'EXPENSE',
    'ASSET',
    'EQUITY',
    'ADJUSTMENT',
  ];

  readonly templateCategories: TemplateCategory[] = [
    'REVENUE',
    'EXPENSE',
    'ASSET',
    'EQUITY',
    'ADJUSTMENT',
  ];

  readonly cashFlowCategories: CashFlowCategory[] = [
    'NONE',
    'OPERATING',
    'INVESTING',
    'FINANCING',
  ];

  readonly allocationTypes: TemplateAllocationType[] = ['FIXED', 'PERCENT', 'BALANCING'];

  readonly availableTemplates = this.api.templates;
  readonly accounts = this.api.accounts;
  readonly periods = signal<PeriodResponse[]>([]);
  readonly recentEntries = this.api.recentEntries;
  readonly selectedSourceEntryId = signal<string>('');

  readonly selectedCategory = signal<'ALL' | TemplateCategory>('ALL');
  readonly searchQuery = signal<string>('');
  readonly currentPage = signal<number>(1);
  readonly selectedTemplate = signal<TemplateResponse | null>(null);
  readonly showCreateForm = signal<boolean>(false);
  readonly submitting = signal<boolean>(false);
  readonly savingTemplate = signal<boolean>(false);
  readonly deletingTemplate = signal<boolean>(false);

  // Poster form state
  reference = '';
  description = '';
  periodId = '';
  postingDate = '2026-11-21';
  readonly baseAmount = signal<number>(100000);
  readonly draftLines = signal<DraftTemplateLine[]>([]);

  // Ledger balance preview state
  private readonly accountBalances = signal<Map<string, number>>(new Map());
  private readonly loadingLedgers = signal<Set<string>>(new Set());

  // Create template form state
  newCode = '';
  newName = '';
  newDescription = '';
  newDefaultDescription = '';
  newReferencePrefix = 'JE-2026-';
  newCategory: TemplateCategory = 'EXPENSE';
  newLines: NewTemplateLineForm[] = [];

  readonly hasPercentLegs = computed(() =>
    this.draftLines().some((l) => l.amountType === 'PERCENT')
  );

  readonly filteredTemplates = computed(() => {
    const cat = this.selectedCategory();
    const q = this.searchQuery().trim().toLowerCase();

    return this.availableTemplates().filter((tpl) => {
      if (cat !== 'ALL' && tpl.category !== cat) {
        return false;
      }
      if (!q) {
        return true;
      }
      const matchesMeta =
        tpl.name.toLowerCase().includes(q) ||
        tpl.code.toLowerCase().includes(q) ||
        tpl.description.toLowerCase().includes(q) ||
        (tpl.referencePrefix || '').toLowerCase().includes(q);

      const matchesLines = tpl.lines.some(
        (line) =>
          line.accountCode.toLowerCase().includes(q) ||
          line.accountName.toLowerCase().includes(q) ||
          line.defaultMemo.toLowerCase().includes(q)
      );

      return matchesMeta || matchesLines;
    });
  });

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredTemplates().length / this.pageSize))
  );

  readonly pagedTemplates = computed(() => {
    const safePage = Math.min(Math.max(this.currentPage(), 1), this.totalPages());
    const start = (safePage - 1) * this.pageSize;
    return this.filteredTemplates().slice(start, start + this.pageSize);
  });

  readonly totalDebits = computed(() =>
    this.draftLines()
      .filter((l) => l.side === 'DEBIT')
      .reduce((sum, l) => sum + (Number(l.amount) || 0), 0)
  );

  readonly totalCredits = computed(() =>
    this.draftLines()
      .filter((l) => l.side === 'CREDIT')
      .reduce((sum, l) => sum + (Number(l.amount) || 0), 0)
  );

  readonly isBalanced = computed(
    () => this.totalDebits() > 0 && Math.abs(this.totalDebits() - this.totalCredits()) < 0.005
  );

  readonly ledgerImpact = computed<LedgerImpactRow[]>(() => {
    const lines = this.draftLines();
    const acctByCode = new Map(this.accounts().map((a) => [a.code, a]));
    const balances = this.accountBalances();
    const loadingSet = this.loadingLedgers();

    const grouped = new Map<
      string,
      {
        accountId: string;
        accountName: string;
        normalSide: NormalSide;
        debitDelta: number;
        creditDelta: number;
      }
    >();

    for (const line of lines) {
      const amt = Number(line.amount) || 0;
      const matched = acctByCode.get(line.accountCode);
      const normalSide: NormalSide =
        matched?.normalSide ??
        (line.accountCode.startsWith('1') || line.accountCode.startsWith('5') ? 'DEBIT' : 'CREDIT');

      const existing = grouped.get(line.accountCode) ?? {
        accountId: matched?.id ?? '',
        accountName: matched?.name || line.accountName || line.accountCode,
        normalSide,
        debitDelta: 0,
        creditDelta: 0,
      };

      if (line.side === 'DEBIT') {
        existing.debitDelta = Number((existing.debitDelta + amt).toFixed(2));
      } else {
        existing.creditDelta = Number((existing.creditDelta + amt).toFixed(2));
      }

      grouped.set(line.accountCode, existing);
    }

    return Array.from(grouped.entries()).map(([accountCode, agg]) => {
      const currentBalance = agg.accountId ? (balances.get(agg.accountId) ?? 0) : 0;
      const netChange =
        agg.normalSide === 'DEBIT'
          ? Number((agg.debitDelta - agg.creditDelta).toFixed(2))
          : Number((agg.creditDelta - agg.debitDelta).toFixed(2));
      const projectedBalance = Number((currentBalance + netChange).toFixed(2));

      return {
        accountId: agg.accountId,
        accountCode,
        accountName: agg.accountName,
        normalSide: agg.normalSide,
        currentBalance,
        debitDelta: agg.debitDelta,
        creditDelta: agg.creditDelta,
        netChange,
        projectedBalance,
        loading: agg.accountId ? loadingSet.has(agg.accountId) : false,
      };
    });
  });

  readonly selectedPeriod = computed(() =>
    this.periods().find((p) => p.id === this.periodId) ?? null
  );

  readonly isSelectedPeriodClosedOrLocked = computed(() => {
    const status = this.selectedPeriod()?.status;
    return status === 'CLOSED' || status === 'LOCKED';
  });

  constructor() {
    effect(() => {
      this.selectedCategory();
      this.searchQuery();
      this.currentPage.set(1);
    });
  }

  ngOnInit(): void {
    this.resetNewTemplateForm();

    if (this.accounts().length === 0) {
      this.api.loadAccounts().subscribe({
        next: () => this.refreshAffectedAccountBalances(),
      });
    }

    this.api.loadTemplates().subscribe({
      next: (list) => {
        if (list.length > 0 && !this.selectedTemplate()) {
          this.selectTemplate(list[0]);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.notifications.fromHttpError(err, 'Failed to Load Templates');
      },
    });

    this.api.loadPeriods().subscribe({
      next: (list) => {
        this.periods.set(list);
        const openPeriod = list.find((p) => p.status === 'OPEN');
        if (openPeriod && !this.periodId) {
          this.periodId = openPeriod.id;
        }
      },
    });

    this.loadRecentEntries();
  }

  loadRecentEntries(): void {
    this.api.loadJournalEntries().subscribe({
      error: () => {
        // Non-blocking if journal list isn't available yet
      },
    });
  }

  refreshAffectedAccountBalances(): void {
    const lines = this.draftLines();
    const dateStr = this.postingDate || new Date().toISOString().slice(0, 10);
    const startStr = `${dateStr.slice(0, 7)}-01`;

    const uniqueAccounts = Array.from(
      new Map(
        lines
          .map((l) => this.accounts().find((a) => a.code === l.accountCode))
          .filter((a): a is NonNullable<typeof a> => Boolean(a?.id))
          .map((a) => [a.id, a])
      ).values()
    );

    for (const acc of uniqueAccounts) {
      this.loadingLedgers.update((s) => {
        const next = new Set(s);
        next.add(acc.id);
        return next;
      });

      this.api.getAccountLedger(acc.id, startStr, dateStr).subscribe({
        next: (ledger) => {
          const bal = Number(ledger.closingBalance) || 0;
          this.accountBalances.update((m) => {
            const next = new Map(m);
            next.set(acc.id, bal);
            return next;
          });
          this.loadingLedgers.update((s) => {
            const next = new Set(s);
            next.delete(acc.id);
            return next;
          });
        },
        error: () => {
          this.loadingLedgers.update((s) => {
            const next = new Set(s);
            next.delete(acc.id);
            return next;
          });
        },
      });
    }
  }

  importEntryIntoTemplateBuilder(entry: JournalEntryResponse): void {
    this.selectedSourceEntryId.set(entry.id);
    this.showCreateForm.set(true);

    const cleanSlug = entry.entryNumber
      .replace(/[^A-Za-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .toUpperCase();

    this.newCode = `FROM_${cleanSlug || 'ENTRY'}`;
    this.newName = entry.description
      ? `${entry.description.slice(0, 42)} Template`
      : `Template from ${entry.entryNumber}`;
    this.newDescription = `Created from posted entry ${entry.entryNumber} (${entry.reference || 'no ref'}).`;
    this.newDefaultDescription = entry.description || `Entry based on ${entry.entryNumber}`;
    this.newReferencePrefix = entry.reference
      ? `${entry.reference.replace(/\d+$/, '')}`
      : 'JE-2026-';

    this.newLines = entry.lines.map((line, idx) => {
      const isDebit = (Number(line.debitAmount) || 0) > 0;
      const amt = isDebit ? Number(line.debitAmount) : Number(line.creditAmount) || 0;
      const isLastLine = idx === entry.lines.length - 1;
      const matchedAcc = this.accounts().find((a) => a.code === line.accountCode);

      return {
        accountCode: line.accountCode,
        accountName: matchedAcc?.name || line.accountName || line.accountCode,
        side: isDebit ? 'DEBIT' : 'CREDIT',
        cashFlowCategory: (line.cashFlowCategory as CashFlowCategory) || 'NONE',
        defaultMemo: line.memo || '',
        defaultAmount: isLastLine ? null : amt,
        amountType: isLastLine ? 'BALANCING' : 'FIXED',
        allocationPercent: null,
      };
    });

    this.notifications.success(
      'Entry Loaded',
      `Imported ${entry.entryNumber} lines into the template builder.`
    );
  }

  onSourceEntrySelected(entryId: string): void {
    this.selectedSourceEntryId.set(entryId);
    const found = this.recentEntries().find((e) => e.id === entryId);
    if (found) {
      this.importEntryIntoTemplateBuilder(found);
    }
  }

  cloneSelectedTemplate(): void {
    const tpl = this.selectedTemplate();
    if (!tpl) {
      return;
    }

    this.selectedSourceEntryId.set('');
    this.showCreateForm.set(true);
    this.newCode = `${tpl.code}_COPY`;
    this.newName = `${tpl.name} (Copy)`;
    this.newDescription = tpl.description;
    this.newDefaultDescription = tpl.defaultDescription;
    this.newReferencePrefix = tpl.referencePrefix || 'JE-2026-';
    this.newCategory = tpl.category;

    this.newLines = tpl.lines.map((line) => {
      const allocType = this.resolveLineAllocationType(line);
      const matchedAcc = this.accounts().find((a) => a.code === line.accountCode);
      return {
        accountCode: line.accountCode,
        accountName: matchedAcc?.name || line.accountName || line.accountCode,
        side: line.side,
        cashFlowCategory: line.cashFlowCategory,
        defaultMemo: line.defaultMemo || '',
        defaultAmount: allocType === 'FIXED' ? (line.defaultAmount ?? null) : null,
        amountType: allocType,
        allocationPercent: allocType === 'PERCENT' ? (line.allocationPercent ?? null) : null,
      };
    });

    this.notifications.success('Template Cloned', `Loaded ${tpl.code} into builder as a copy.`);
  }

  deleteSelectedTemplate(): void {
    const tpl = this.selectedTemplate();
    if (!tpl || this.deletingTemplate()) {
      return;
    }

    this.deletingTemplate.set(true);
    this.api.deleteTemplate(tpl.id).subscribe({
      next: () => {
        this.deletingTemplate.set(false);
        this.notifications.success('Template Deleted', `Removed ${tpl.code}`);
        const remaining = this.availableTemplates();
        if (remaining.length > 0) {
          this.selectTemplate(remaining[0]);
        } else {
          this.selectedTemplate.set(null);
          this.draftLines.set([]);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.deletingTemplate.set(false);
        this.notifications.fromHttpError(err, 'Delete Template Failed');
      },
    });
  }

  private resolveLineAllocationType(line: TemplateLineDef): TemplateAllocationType {
    if (line.amountType) {
      return line.amountType;
    }
    return line.balancingLeg ? 'BALANCING' : 'FIXED';
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.selectedCategory.set('ALL');
    this.currentPage.set(1);
  }

  goToPage(page: number): void {
    const clamped = Math.min(Math.max(page, 1), this.totalPages());
    this.currentPage.set(clamped);
  }

  toggleCreateForm(): void {
    const next = !this.showCreateForm();
    this.showCreateForm.set(next);
    if (next && this.newLines.length === 0) {
      this.resetNewTemplateForm();
    }
  }

  resetNewTemplateForm(): void {
    const firstAcc = this.accounts()[0];
    const secondAcc = this.accounts()[1] ?? firstAcc;
    this.selectedSourceEntryId.set('');
    this.newCode = '';
    this.newName = '';
    this.newDescription = '';
    this.newDefaultDescription = '';
    this.newReferencePrefix = 'JE-2026-';
    this.newCategory = 'EXPENSE';
    this.newLines = [
      {
        accountCode: firstAcc?.code || '5010',
        accountName: firstAcc?.name || 'Office Rent & Utilities',
        side: 'DEBIT',
        cashFlowCategory: 'NONE',
        defaultMemo: '',
        defaultAmount: null,
        amountType: 'FIXED',
        allocationPercent: null,
      },
      {
        accountCode: secondAcc?.code || '1010',
        accountName: secondAcc?.name || 'Operating Bank Account',
        side: 'CREDIT',
        cashFlowCategory: 'OPERATING',
        defaultMemo: '',
        defaultAmount: null,
        amountType: 'BALANCING',
        allocationPercent: null,
      },
    ];
  }

  addNewTemplateLine(): void {
    this.newLines.push({
      accountCode: this.accounts()[0]?.code || '1010',
      accountName: this.accounts()[0]?.name || 'Operating Bank Account',
      side: 'DEBIT',
      cashFlowCategory: 'NONE',
      defaultMemo: '',
      defaultAmount: null,
      amountType: 'FIXED',
      allocationPercent: null,
    });
  }

  removeNewTemplateLine(index: number): void {
    if (this.newLines.length > 2) {
      this.newLines.splice(index, 1);
    }
  }

  onNewLineAccountChange(line: NewTemplateLineForm): void {
    const matched = this.accounts().find((a) => a.code === line.accountCode);
    if (matched) {
      line.accountName = matched.name;
    }
  }

  onNewLineAllocationTypeChange(line: NewTemplateLineForm): void {
    if (line.amountType === 'BALANCING') {
      line.defaultAmount = null;
      line.allocationPercent = null;
    } else if (line.amountType === 'PERCENT') {
      line.defaultAmount = null;
      if (!line.allocationPercent) {
        line.allocationPercent = 18;
      }
    } else {
      line.allocationPercent = null;
    }
  }

  submitNewTemplate(): void {
    const code = this.newCode.trim().toUpperCase();
    const name = this.newName.trim();
    const defaultDescription = this.newDefaultDescription.trim();

    if (!code || !name || !defaultDescription) {
      this.notifications.error(
        'Missing Fields',
        'Template code, name, and default description are required.'
      );
      return;
    }

    for (const l of this.newLines) {
      if (l.amountType === 'PERCENT' && (!l.allocationPercent || l.allocationPercent <= 0)) {
        this.notifications.error(
          'Invalid Percentage',
          `Enter a positive allocation % for account ${l.accountCode}.`
        );
        return;
      }
    }

    const lines: TemplateLineDef[] = this.newLines.map((l) => {
      const matched = this.accounts().find((a) => a.code === l.accountCode);
      const isBalancer = l.amountType === 'BALANCING';
      return {
        accountCode: l.accountCode.trim(),
        accountName: matched?.name || l.accountName.trim() || l.accountCode.trim(),
        side: l.side,
        cashFlowCategory: l.cashFlowCategory,
        defaultMemo: l.defaultMemo.trim(),
        defaultAmount: l.amountType === 'FIXED' ? (l.defaultAmount ?? null) : null,
        balancingLeg: isBalancer,
        amountType: l.amountType,
        allocationPercent: l.amountType === 'PERCENT' ? Number(l.allocationPercent) : null,
      };
    });

    const payload: CreateTemplateRequest = {
      code,
      name,
      description: this.newDescription.trim() || defaultDescription,
      defaultDescription,
      referencePrefix: this.newReferencePrefix.trim() || 'JE-',
      category: this.newCategory,
      lines,
    };

    this.savingTemplate.set(true);
    this.api.createTemplate(payload).subscribe({
      next: (created) => {
        this.savingTemplate.set(false);
        this.showCreateForm.set(false);
        this.selectTemplate(created);
        this.resetNewTemplateForm();
        this.notifications.success('Template Created', `Saved ${created.code}`);
      },
      error: (err: HttpErrorResponse) => {
        this.savingTemplate.set(false);
        this.notifications.fromHttpError(err, 'Create Template Failed');
      },
    });
  }

  selectTemplate(tpl: TemplateResponse): void {
    this.selectedTemplate.set(tpl);
    this.regenerateReference(tpl);
    this.description = tpl.defaultDescription;

    const mapped: DraftTemplateLine[] = tpl.lines.map((line) => {
      const matchedAcc = this.accounts().find((a) => a.code === line.accountCode);
      const allocType = this.resolveLineAllocationType(line);
      return {
        accountCode: line.accountCode,
        accountName: matchedAcc?.name || line.accountName,
        side: line.side,
        cashFlowCategory: line.cashFlowCategory,
        memo: line.defaultMemo,
        amount: line.defaultAmount ?? 0,
        amountType: allocType,
        allocationPercent: line.allocationPercent ?? null,
        balancingLeg: allocType === 'BALANCING',
      };
    });

    this.draftLines.set(mapped);
    this.recalculateAllocations();
    this.refreshAffectedAccountBalances();
  }

  regenerateReference(tpl = this.selectedTemplate()): void {
    const prefix = tpl?.referencePrefix || 'JE-';
    const suffix = String(Math.floor(100 + Math.random() * 900));
    this.reference = `${prefix}${suffix}`;
  }

  onBaseAmountChange(value: number | string): void {
    this.baseAmount.set(Math.max(0, Number(value) || 0));
    this.recalculateAllocations();
  }

  updateLineAmount(index: number, value: number | string): void {
    const amt = Math.max(0, Number(value) || 0);
    this.draftLines.update((lines) =>
      lines.map((l, idx) => (idx === index ? { ...l, amount: amt } : l))
    );
    this.recalculateAllocations();
  }

  updateLineMemo(index: number, memo: string): void {
    this.draftLines.update((lines) =>
      lines.map((l, idx) => (idx === index ? { ...l, memo } : l))
    );
  }

  recalculateAllocations(): void {
    const base = this.baseAmount();

    let updated = this.draftLines().map((line) => {
      if (line.amountType !== 'PERCENT') {
        return line;
      }
      const pct = Number(line.allocationPercent) || 0;
      const computed = Number(((base * pct) / 100).toFixed(2));
      return { ...line, amount: computed };
    });

    const balancerIndex = updated.findIndex((l) => l.amountType === 'BALANCING');
    if (balancerIndex !== -1) {
      const balancer = updated[balancerIndex];
      const otherDebits = updated
        .filter((l, idx) => l.side === 'DEBIT' && (l.side !== balancer.side || idx !== balancerIndex))
        .reduce((sum, l) => sum + (Number(l.amount) || 0), 0);

      const otherCredits = updated
        .filter((l, idx) => l.side === 'CREDIT' && (l.side !== balancer.side || idx !== balancerIndex))
        .reduce((sum, l) => sum + (Number(l.amount) || 0), 0);

      const computedAmount =
        balancer.side === 'CREDIT'
          ? Math.max(0, Number((otherDebits - otherCredits).toFixed(2)))
          : Math.max(0, Number((otherCredits - otherDebits).toFixed(2)));

      updated = updated.map((l, idx) =>
        idx === balancerIndex ? { ...balancer, amount: computedAmount } : l
      );
    }

    this.draftLines.set(updated);
  }

  saveCurrentAsTemplate(): void {
    const tpl = this.selectedTemplate();
    if (!tpl) {
      return;
    }

    const uniqueCode = `${tpl.code}_CUSTOM_${Math.floor(100 + Math.random() * 900)}`;
    const req: CreateTemplateRequest = {
      code: uniqueCode,
      name: `${tpl.name} (Custom)`,
      description: tpl.description,
      defaultDescription: this.description.trim() || tpl.defaultDescription,
      referencePrefix: tpl.referencePrefix,
      category: tpl.category,
      lines: this.draftLines().map((line) => ({
        accountCode: line.accountCode,
        accountName: line.accountName,
        side: line.side,
        cashFlowCategory: line.cashFlowCategory,
        defaultMemo: line.memo,
        defaultAmount: line.amountType === 'FIXED' ? Number(line.amount) || 0 : null,
        balancingLeg: line.amountType === 'BALANCING',
        amountType: line.amountType,
        allocationPercent: line.amountType === 'PERCENT' ? line.allocationPercent : null,
      })),
    };

    this.savingTemplate.set(true);
    this.api.createTemplate(req).subscribe({
      next: (created) => {
        this.savingTemplate.set(false);
        this.selectTemplate(created);
        this.notifications.success('Template Saved', `Created ${created.code}`);
      },
      error: (err: HttpErrorResponse) => {
        this.savingTemplate.set(false);
        this.notifications.fromHttpError(err, 'Save Template Failed');
      },
    });
  }

  postEntry(): void {
    const tpl = this.selectedTemplate();
    if (!tpl || !this.isBalanced()) {
      return;
    }

    const lines: PostJournalLineRequest[] = [];
    for (const draft of this.draftLines()) {
      const account = this.accounts().find((a) => a.code === draft.accountCode);
      if (!account) {
        this.notifications.error(
          'Missing Account',
          `Account code ${draft.accountCode} was not found in the chart of accounts.`
        );
        return;
      }

      const amt = Number(draft.amount) || 0;
      lines.push({
        accountId: account.id,
        accountCode: account.code,
        debitAmount: draft.side === 'DEBIT' ? amt : 0,
        creditAmount: draft.side === 'CREDIT' ? amt : 0,
        memo: draft.memo,
        cashFlowCategory: draft.cashFlowCategory,
      });
    }

    const postingIso = new Date(`${this.postingDate}T12:00:00.000Z`).toISOString();
    const payload: PostJournalEntryRequest = {
      reference: this.reference.trim(),
      sourceType: 'MANUAL',
      postingDate: postingIso,
      transactionDate: postingIso,
      description: this.description.trim(),
      periodId: this.periodId || undefined,
      createdBy: 'ui-template-poster',
      lines,
    };

    this.submitting.set(true);
    this.api.postJournalEntry(payload).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.notifications.success('Journal Entry Posted', `Created ${created.entryNumber}`);
        this.regenerateReference(tpl);
        this.loadRecentEntries();
        this.refreshAffectedAccountBalances();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.notifications.fromHttpError(err, 'Post Failed');
      },
    });
  }
}