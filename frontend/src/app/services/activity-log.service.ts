import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import type { Observable } from 'rxjs';
import type { ApiError } from '../models';

export interface LogEntry {
  id: number;
  label: string;
  time: Date;
  isSuccess: boolean;
  data?: unknown;
  error?: ApiError;
}

const MAX_ENTRIES = 30;

function toApiError(err: unknown): ApiError {
  if (!(err instanceof HttpErrorResponse)) {
    return { status: 0, message: 'Unexpected error' };
  }

  const body: unknown = err.error;

  if (body && typeof body === 'object' && 'message' in body) {
    return body as ApiError;
  }

  if (err.status === 0) {
    return { status: 0, message: 'Could not reach the server. Are the services running?' };
  }

  return { status: err.status, message: err.statusText || 'Request failed' };
}

// Every panel sends its requests through here, so all responses show up in one place.
@Injectable({ providedIn: 'root' })
export class ActivityLogService {
  private nextId = 1;
  readonly entries = signal<LogEntry[]>([]);

  run<T>(label: string, request: Observable<T>): void {
    request.subscribe({
      next: (data) => this.add({ label, isSuccess: true, data }),
      error: (err: unknown) => this.add({ label, isSuccess: false, error: toApiError(err) }),
    });
  }

  clear(): void {
    this.entries.set([]);
  }

  private add(entry: Omit<LogEntry, 'id' | 'time'>): void {
    const newEntry: LogEntry = { ...entry, id: this.nextId++, time: new Date() };
    this.entries.update((entries) => [newEntry, ...entries].slice(0, MAX_ENTRIES));
  }
}
