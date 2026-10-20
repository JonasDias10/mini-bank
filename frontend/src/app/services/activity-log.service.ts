import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import type { Observable } from 'rxjs';
import type { ApiError } from '../models';

export type LogTone = 'success' | 'pending' | 'failure';

export interface LogEntry {
  id: number;
  label: string;
  time: Date;
  tone: LogTone;
  tag: string;
  data?: unknown;
  error?: ApiError;
}

const MAX_ENTRIES = 30;

const TONE_BY_TRANSACTION_STATUS: Record<string, LogTone> = {
  PENDING: 'pending',
  FAILED: 'failure',
};

function transactionStatusOf(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'status' in data && typeof data.status === 'string') {
    return data.status;
  }

  return undefined;
}

function toApiError(err: unknown): ApiError {
  if (err instanceof HttpErrorResponse) {
    return fromHttpError(err);
  }

  if (err instanceof Error) {
    return { status: 0, message: err.message };
  }

  return { status: 0, message: 'Unexpected error' };
}

function fromHttpError(err: HttpErrorResponse): ApiError {
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
      next: (data) => this.addResponse(label, data),
      error: (err: unknown) => this.addError(label, toApiError(err)),
    });
  }

  clear(): void {
    this.entries.set([]);
  }

  private addResponse(label: string, data: unknown): void {
    const status = transactionStatusOf(data);
    const tone = (status ? TONE_BY_TRANSACTION_STATUS[status] : undefined) ?? 'success';
    this.add({ label, data, tone, tag: status ?? 'OK' });
  }

  private addError(label: string, error: ApiError): void {
    this.add({ label, error, tone: 'failure', tag: error.status ? String(error.status) : 'ERROR' });
  }

  private add(entry: Omit<LogEntry, 'id' | 'time'>): void {
    const newEntry: LogEntry = { ...entry, id: this.nextId++, time: new Date() };
    this.entries.update((entries) => [newEntry, ...entries].slice(0, MAX_ENTRIES));
  }
}
