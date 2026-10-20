export interface CreateCustomerRequest {
  fullName: string;
  email: string;
}

export interface CustomerResponse {
  id: number;
  fullName: string;
  email: string;
  createdAt: string;
}

export interface AccountResponse {
  id: number;
  accountNumber: string;
  balance: number;
  customerId: number;
  customerName: string;
  createdAt: string;
}

export interface BalanceResponse {
  accountId: number;
  accountNumber: string;
  balance: number;
}

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';

export interface TransactionResponse {
  id: number;
  type: TransactionType;
  sourceAccountId?: number;
  targetAccountId?: number;
  amount: number;
  createdAt: string;
}

export interface AccountOperationRequest {
  accountId: number | null;
  amount: number | null;
}

export interface TransferRequest {
  fromAccountId: number | null;
  toAccountId: number | null;
  amount: number | null;
}

export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
