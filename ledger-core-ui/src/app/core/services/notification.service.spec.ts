import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [NotificationService] });
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('adds success notifications and dismisses them after their timeout', () => {
    service.success('Account created', 'Account Created', 1000);
    const [message] = service.messages();

    expect(message).toMatchObject({
      kind: 'success',
      title: 'Account Created',
      detail: 'Account created',
    });

    vi.advanceTimersByTime(1000);
    expect(service.messages()).toEqual([]);
  });

  it('maps problem details and correlation IDs from HTTP errors', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        title: 'Invalid account',
        detail: 'Code is already in use',
        correlationId: 'corr-123',
      },
    });

    service.fromHttpError(error);

    expect(service.messages()[0]).toMatchObject({
      kind: 'error',
      title: 'Invalid account',
      detail: 'Code is already in use',
      correlationId: 'corr-123',
    });
  });

  it('uses the fallback title and HTTP message when no problem details exist', () => {
    service.fromHttpError(
      new HttpErrorResponse({ status: 500, statusText: 'Server Error' }),
      'Statement Error'
    );

    expect(service.messages()[0]).toMatchObject({
      kind: 'error',
      title: 'Statement Error',
      detail: expect.stringContaining('500'),
    });
  });

  it('supports dismissing one message and clearing the rest', () => {
    service.info('First');
    service.error('Second');
    const firstId = service.messages()[0].id;

    service.dismiss(firstId);
    expect(service.messages()).toHaveLength(1);
    expect(service.messages()[0].detail).toBe('Second');

    service.clear();
    expect(service.messages()).toEqual([]);
  });
});
