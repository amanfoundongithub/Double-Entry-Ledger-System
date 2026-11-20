import { HttpInterceptorFn } from '@angular/common/http';

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export const ledgerHeadersInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/v1/')) {
    return next(req);
  }

  const headersToSet: Record<string, string> = {};

  if (!req.headers.has('X-Correlation-Id')) {
    headersToSet['X-Correlation-Id'] = crypto.randomUUID();
  }

  if (MUTATING_METHODS.has(req.method.toUpperCase()) && !req.headers.has('Idempotency-Key')) {
    headersToSet['Idempotency-Key'] = crypto.randomUUID();
  }

  return next(req.clone({ setHeaders: headersToSet }));
};