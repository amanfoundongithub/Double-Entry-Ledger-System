export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
export type NormalSide = 'DEBIT' | 'CREDIT';
export type CashFlowCategory = 'OPERATING' | 'INVESTING' | 'FINANCING' | 'NONE';
export type EntryStatus = 'POSTED' | 'REVERSED';
export type PeriodStatus = 'OPEN' | 'CLOSED' | 'LOCKED';

export interface Account {
  id: string;
  code: string;
  name: string;
  type: AccountType;
  normalSide: NormalSide;
  cashFlowCategory: CashFlowCategory;
  cashEquivalent?: boolean;
  contra?: boolean;
  currency: string;
  description?: string | null;
  active: boolean;
  parentAccountId?: string | null;
}

export interface CreateAccountRequest {
  code: string;
  name: string;
  type: AccountType;
  normalSide: NormalSide;
  cashFlowCategory: CashFlowCategory;
  cashEquivalent?: boolean;
  contra?: boolean;                 // Match backend Java record field
  currency: string;
  description?: string | null;      // Match backend Java record field
  parentAccountId?: string | null;  // Keep optional if backend entity supports it later
}

export interface JournalLineResponse {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  debitAmount: string;
  creditAmount: string;
  memo?: string | null;
  cashFlowCategory: CashFlowCategory;
}

export interface JournalEntryResponse {
  id: string;
  entryNumber: string;
  reference?: string | null;
  sourceType: string;
  status: EntryStatus;
  postingDate: string;
  transactionDate: string;
  description: string;
  periodId: string;
  reversalOfEntryId?: string | null;
  reversedByEntryId?: string | null;
  idempotencyKey: string;
  lines: JournalLineResponse[];
  totalAmount: string;
  createdAt: string;
  createdBy: string;
}

export interface PostJournalLineRequest {
  accountId?: string;
  accountCode?: string;
  debitAmount: number;
  creditAmount: number;
  memo?: string;
  cashFlowCategory?: CashFlowCategory;
}

export interface PostJournalEntryRequest {
  reference?: string;
  sourceType?: string;
  postingDate?: string;
  transactionDate?: string;
  description: string;
  periodId?: string;
  createdBy?: string;
  lines: PostJournalLineRequest[];
}

export interface ReverseJournalEntryRequest {
  reason?: string;
  createdBy?: string;
}

export interface PeriodResponse {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: PeriodStatus;
  closedAt?: string | null;
  closedBy?: string | null;
}

export interface CreatePeriodRequest {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
}

export interface TrialBalanceLine {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  normalSide: NormalSide;
  contra?: boolean;
  totalDebits: number | string;
  totalCredits: number | string;
  netBalance: number | string;
}

export interface TrialBalanceResponse {
  asOfDate: string;
  rows: TrialBalanceLine[];
  totalDebits: number | string;
  totalCredits: number | string;
  balanced: boolean;
}

export interface StatementAccountLine {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  amount: string;
}

export interface IncomeStatementResponse {
  startDate: string;
  endDate: string;
  revenues: StatementAccountLine[];
  expenses: StatementAccountLine[];
  totalRevenue: string;
  totalExpenses: string;
  netIncome: string;
}

export interface BalanceSheetResponse {
  asOfDate: string;
  assets: StatementAccountLine[];
  liabilities: StatementAccountLine[];
  equity: StatementAccountLine[];
  totalAssets: string;
  totalLiabilities: string;
  cumulativeUnclosedNetIncome: string;
  totalEquity: string;
  balanced: boolean;
}

export interface CashFlowLine {
  accountId: string;
  accountCode: string;
  accountName: string;
  category: CashFlowCategory;
  amount: string;
}

export interface CashFlowStatementResponse {
  startDate: string;
  endDate: string;
  operatingActivities: CashFlowLine[];
  investingActivities: CashFlowLine[];
  financingActivities: CashFlowLine[];
  operatingCashFlow: string;
  investingCashFlow: string;
  financingCashFlow: string;
  netCashChange: string;
  beginningCashBalance: string;
  endingCashBalance: string;
  reconciled: boolean;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  timestamp?: string;
}

export interface AccountLedgerEntryLine {
  journalEntryId: string;
  entryNumber: string;
  reference?: string | null;
  entryStatus: string;
  postingDate: string;
  transactionDate: string;
  periodId: string;
  description: string;
  memo?: string | null;
  debitAmount: number | string;
  creditAmount: number | string;
  runningBalance: number | string;
}

export interface AccountLedgerResponse {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  normalSide: NormalSide;
  contra?: boolean;
  startDate: string;
  endDate: string;
  openingBalance: number | string;
  totalDebits: number | string;
  totalCredits: number | string;
  closingBalance: number | string;
  entries: AccountLedgerEntryLine[];
}