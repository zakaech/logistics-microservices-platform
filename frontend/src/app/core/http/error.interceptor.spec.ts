import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { toProblemDetail } from './error.interceptor';

/**
 * Every failure must reach a component as a ProblemDetail, including the ones the backend never
 * sees. A component that had to branch on "did this come from the server or the browser?" would
 * branch wrongly.
 */
describe('toProblemDetail', () => {
  it('passes a real problem+json body through unchanged', () => {
    const problem = {
      type: 'https://logistics.local/problems/allocation-failed',
      title: 'Allocation failed',
      status: 409,
      detail: 'No combination of warehouses can fulfil this order.',
    };

    const result = toProblemDetail(
      new HttpErrorResponse({ status: 409, error: problem }),
    );

    expect(result).toEqual(problem);
  });

  it('turns an unreachable server into a problem the UI can render', () => {
    // Status 0: offline, DNS failure or a CORS refusal. There is no body to read.
    const result = toProblemDetail(new HttpErrorResponse({ status: 0 }));

    expect(result.status).toBe(0);
    expect(result.title).toBe('Network unreachable');
    expect(result.detail).toContain('could not be reached');
  });

  it('keeps what the server said when the body is not problem+json', () => {
    const result = toProblemDetail(
      new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway', error: 'upstream died' }),
    );

    expect(result.status).toBe(502);
    expect(result.title).toBe('Bad Gateway');
    expect(result.detail).toBe('upstream died');
  });

  it('preserves the structured extras a component needs to explain the failure', () => {
    const result = toProblemDetail(
      new HttpErrorResponse({
        status: 409,
        error: {
          type: 'x',
          title: 'Allocation failed',
          status: 409,
          detail: 'd',
          unsatisfiedLines: [
            { productId: 'p1', requested: 10, availableAcrossNetwork: 6 },
          ],
        },
      }),
    );

    expect(result.unsatisfiedLines?.[0].availableAcrossNetwork).toBe(6);
  });
});
