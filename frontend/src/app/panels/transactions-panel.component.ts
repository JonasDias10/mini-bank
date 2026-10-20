import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { AccountOperationRequest, TransferRequest } from '../models';
import { ActivityLogService } from '../services/activity-log.service';
import { TransactionApiService } from '../services/transaction-api.service';

@Component({
  selector: 'app-transactions-panel',
  imports: [FormsModule],
  template: `
    <section class="card">
      <h2>Transactions</h2>

      <form (ngSubmit)="handleDeposit()">
        <h3>Deposit / Withdraw</h3>
        <label>Account id <input name="opAccount" type="number" [(ngModel)]="operation.accountId" /></label>
        <label>Amount <input name="opAmount" type="number" step="0.01" [(ngModel)]="operation.amount" /></label>
        <button type="submit">Deposit</button>
        <button type="button" (click)="handleWithdraw()">Withdraw</button>
      </form>

      <form (ngSubmit)="handleTransfer()">
        <h3>Transfer</h3>
        <label>From account <input name="from" type="number" [(ngModel)]="transfer.fromAccountId" /></label>
        <label>To account <input name="to" type="number" [(ngModel)]="transfer.toAccountId" /></label>
        <label>Amount <input name="trAmount" type="number" step="0.01" [(ngModel)]="transfer.amount" /></label>
        <button type="submit">Transfer</button>
      </form>

      <form (ngSubmit)="handleHistory()">
        <h3>History</h3>
        <label>Account id <input name="hAccount" type="number" [(ngModel)]="historyAccountId" /></label>
        <label>Page <input name="page" type="number" min="0" [(ngModel)]="page" /></label>
        <label>Size <input name="size" type="number" min="1" max="100" [(ngModel)]="size" /></label>
        <button type="submit">Get history</button>
      </form>

      <form (ngSubmit)="handleFind()">
        <h3>Find transaction</h3>
        <label>Transaction id <input name="txId" type="number" [(ngModel)]="transactionId" /></label>
        <button type="submit">Get transaction</button>
      </form>
    </section>
  `,
})
export class TransactionsPanelComponent {
  private readonly api = inject(TransactionApiService);
  private readonly log = inject(ActivityLogService);

  operation: AccountOperationRequest = { accountId: null, amount: null };
  transfer: TransferRequest = { fromAccountId: null, toAccountId: null, amount: null };
  historyAccountId: number | null = null;
  page = 0;
  size = 20;
  transactionId: number | null = null;

  handleDeposit(): void {
    this.log.run(`Deposit into account ${this.operation.accountId}`, this.api.deposit({ ...this.operation }));
  }

  handleWithdraw(): void {
    this.log.run(`Withdraw from account ${this.operation.accountId}`, this.api.withdraw({ ...this.operation }));
  }

  handleTransfer(): void {
    const { fromAccountId, toAccountId } = this.transfer;
    this.log.run(`Transfer ${fromAccountId} -> ${toAccountId}`, this.api.transfer({ ...this.transfer }));
  }

  handleHistory(): void {
    this.log.run(
      `History of account ${this.historyAccountId}`,
      this.api.getHistory(this.historyAccountId, this.page, this.size),
    );
  }

  handleFind(): void {
    this.log.run(`Get transaction ${this.transactionId}`, this.api.getTransaction(this.transactionId));
  }
}
