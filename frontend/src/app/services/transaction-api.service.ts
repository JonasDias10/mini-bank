import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { concat, first, of, switchMap, throwError, timeout, timer, type Observable } from 'rxjs';
import type { AccountOperationRequest, TransactionResponse, TransferRequest } from '../models';

const POLL_INTERVAL_MS = 500;
const SETTLE_TIMEOUT_MS = 15_000;

@Injectable({ providedIn: 'root' })
export class TransactionApiService {
  private readonly http = inject(HttpClient);

  deposit(request: AccountOperationRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/transactions/deposit', request);
  }

  withdraw(request: AccountOperationRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/transactions/withdraw', request);
  }

  transfer(request: TransferRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/transactions/transfer', request);
  }

  getTransaction(id: number | null): Observable<TransactionResponse> {
    return this.http.get<TransactionResponse>(`/api/transactions/${id}`);
  }

  getHistory(accountId: number | null, page: number, size: number): Observable<TransactionResponse[]> {
    const params = new HttpParams()
      .set('accountId', accountId ?? '')
      .set('page', page)
      .set('size', size);
    return this.http.get<TransactionResponse[]>('/api/transactions', { params });
  }

  // The API answers 202 with a PENDING transaction. Emit it right away, then again once the saga has settled.
  trackUntilSettled(request: Observable<TransactionResponse>): Observable<TransactionResponse> {
    return request.pipe(switchMap((accepted) => concat(of(accepted), this.waitUntilSettled(accepted.id))));
  }

  private waitUntilSettled(id: number): Observable<TransactionResponse> {
    return timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS).pipe(
      switchMap(() => this.getTransaction(id)),
      first((transaction) => transaction.status !== 'PENDING'),
      timeout({
        first: SETTLE_TIMEOUT_MS,
        with: () =>
          throwError(() => new Error(`Transaction ${id} is still pending. Use "Find transaction" to check it later.`)),
      }),
    );
  }
}
