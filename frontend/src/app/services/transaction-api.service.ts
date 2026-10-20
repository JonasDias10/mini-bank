import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import type { AccountOperationRequest, TransactionResponse, TransferRequest } from '../models';

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
}
