import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { ProblemDetail } from '../models/ledger.models';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: string;
  kind: ToastKind;
  title: string;
  detail: string;
  correlationId?: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messagesSignal = signal<ToastMessage[]>([]);
  readonly messages = this.messagesSignal.asReadonly();

  success(detail: string, title = 'Success', autoDismissMs = 4500): void {
    this.push('success', title, detail, undefined, autoDismissMs);
  }

  info(detail: string, title = 'Info', autoDismissMs = 4500): void {
    this.push('info', title, detail, undefined, autoDismissMs);
  }

  error(detail: string, title = 'Error', correlationId?: string): void {
    this.push('error', title, detail, correlationId);
  }

  fromHttpError(err: HttpErrorResponse, fallbackTitle = 'Request Failed'): void {
    const problem = err.error as ProblemDetail | null;
    if (problem && (problem.detail || problem.title)) {
      this.error(
        problem.detail || `HTTP ${problem.status ?? err.status}`,
        problem.title || fallbackTitle,
        problem.correlationId
      );
      return;
    }

    const detail =
      typeof err.error === 'string' && err.error.trim().length > 0
        ? err.error
        : err.message || `Unexpected HTTP error (${err.status})`;
    this.error(detail, fallbackTitle);
  }

  dismiss(id: string): void {
    this.messagesSignal.update((list) => list.filter((item) => item.id !== id));
  }

  clear(): void {
    this.messagesSignal.set([]);
  }

  private push(
    kind: ToastKind,
    title: string,
    detail: string,
    correlationId?: string,
    autoDismissMs?: number
  ): void {
    const id = crypto.randomUUID();
    const toast: ToastMessage = { id, kind, title, detail, correlationId };
    this.messagesSignal.update((list) => [...list, toast]);

    if (autoDismissMs && autoDismissMs > 0) {
      setTimeout(() => this.dismiss(id), autoDismissMs);
    }
  }
}