import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CreatePeriodRequest, PeriodResponse } from '../../core/models/ledger.models';
import { LedgerApiService } from '../../core/services/ledger-api.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'app-periods',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './periods.component.html',
  styleUrl: './periods.component.scss',
})
export class PeriodsComponent implements OnInit {
  private readonly api = inject(LedgerApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  readonly periods = this.api.periods;
  readonly busyId = signal<string | null>(null);
  readonly creating = signal<boolean>(false);

  readonly form = this.fb.nonNullable.group({
    id: ['', [Validators.required, Validators.minLength(4)]],
    name: ['', [Validators.required, Validators.minLength(3)]],
    startDate: ['2026-11-01', Validators.required],
    endDate: ['2026-11-30', Validators.required],
  });

  ngOnInit(): void {
    this.api.loadPeriods().subscribe({
      error: (err: HttpErrorResponse) =>
        this.notifications.fromHttpError(err, 'Failed to Load Periods'),
    });
  }

  onCreate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifications.error(
        'Please enter a valid period ID, name, start date, and end date.',
        'Validation Failed'
      );
      return;
    }

    this.creating.set(true);

    const raw = this.form.getRawValue();
    const payload: CreatePeriodRequest = {
      id: raw.id.trim(),
      name: raw.name.trim(),
      startDate: new Date(`${raw.startDate}T00:00:00Z`).toISOString(),
      endDate: new Date(`${raw.endDate}T23:59:59.999Z`).toISOString(),
    };

    this.api.createPeriod(payload).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.notifications.success(
          `Created accounting period ${created.id} (${created.name})`,
          'Period Created'
        );
        this.form.patchValue({ id: '', name: '' });
      },
      error: (err: HttpErrorResponse) => {
        this.creating.set(false);
        this.notifications.fromHttpError(err, 'Create Period Failed');
      },
    });
  }

  onClose(period: PeriodResponse): void {
    this.runPeriodAction(period.id, () => this.api.closePeriod(period.id), `Closed period ${period.id}`);
  }

  onLock(period: PeriodResponse): void {
    this.runPeriodAction(period.id, () => this.api.lockPeriod(period.id), `Locked period ${period.id}`);
  }

  onReopen(period: PeriodResponse): void {
    this.runPeriodAction(period.id, () => this.api.reopenPeriod(period.id), `Reopened period ${period.id}`);
  }

  private runPeriodAction(
    periodId: string,
    action: () => ReturnType<LedgerApiService['closePeriod']>,
    doneMsg: string
  ): void {
    this.busyId.set(periodId);

    action().subscribe({
      next: () => {
        this.busyId.set(null);
        this.notifications.success(doneMsg, 'Period Updated');
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.notifications.fromHttpError(err, 'Period Update Failed');
      },
    });
  }
}