import { Routes } from '@angular/router';
import { AccountsComponent } from './features/accounts/accounts.component';
import { JournalComposerComponent } from './features/journal/journal-composer.component';
import { PeriodsComponent } from './features/periods/periods.component';
import { StatementsComponent } from './features/statements/statements.component';

export const routes: Routes = [
  { path: '', redirectTo: 'journal', pathMatch: 'full' },
  { path: 'journal', component: JournalComposerComponent },
  { path: 'accounts', component: AccountsComponent },
  { path: 'periods', component: PeriodsComponent },
  { path: 'statements', component: StatementsComponent },
  { path: '**', redirectTo: 'journal' },
];