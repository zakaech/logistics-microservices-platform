import { signal } from '@angular/core';
import { ProblemDetail } from '../core/models';

/**
 * The three things every screen in this app needs to know about a request: is it running, did it
 * fail, and what came back.
 *
 * <p>Written once because otherwise every component grows its own {@code loading} boolean and
 * {@code error} string, and they drift: one forgets to clear the error before retrying, another
 * leaves the spinner up after a failure. Making the transitions the only way to change the state
 * removes that whole class of bug.
 */
export class RequestState<T> {
  private readonly _data = signal<T | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly data = this._data.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  constructor(initial: T | null = null) {
    this._data.set(initial);
  }

  /** Starting clears the previous error: a retry must not still show why the last attempt failed. */
  start(): void {
    this._loading.set(true);
    this._error.set(null);
  }

  succeed(value: T): void {
    this._data.set(value);
    this._loading.set(false);
    this._error.set(null);
  }

  /**
   * Records a failure and stops the spinner, keeping whatever data was already displayed. Blanking
   * a populated table because a refresh failed loses information the user still had.
   */
  fail(problem: ProblemDetail): void {
    this._loading.set(false);
    this._error.set(problem);
  }

  reset(): void {
    this._data.set(null);
    this._loading.set(false);
    this._error.set(null);
  }

  hasData(): boolean {
    return this._data() !== null;
  }
}
