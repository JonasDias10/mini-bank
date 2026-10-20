import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import type {
  AccountResponse,
  BalanceResponse,
  CreateCustomerRequest,
  CustomerResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class AccountApiService {
  private readonly http = inject(HttpClient);

  createCustomer(request: CreateCustomerRequest): Observable<CustomerResponse> {
    return this.http.post<CustomerResponse>('/api/customers', request);
  }

  getCustomer(id: number | null): Observable<CustomerResponse> {
    return this.http.get<CustomerResponse>(`/api/customers/${id}`);
  }

  getCustomerAccounts(customerId: number | null): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>(`/api/customers/${customerId}/accounts`);
  }

  createAccount(customerId: number | null): Observable<AccountResponse> {
    return this.http.post<AccountResponse>('/api/accounts', { customerId });
  }

  getAccount(id: number | null): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`/api/accounts/${id}`);
  }

  getBalance(id: number | null): Observable<BalanceResponse> {
    return this.http.get<BalanceResponse>(`/api/accounts/${id}/balance`);
  }
}
