import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AccountApiService } from '../services/account-api.service';
import { ActivityLogService } from '../services/activity-log.service';

@Component({
  selector: 'app-accounts-panel',
  imports: [FormsModule],
  template: `
    <section class="card">
      <h2>Accounts</h2>

      <form (ngSubmit)="handleCreate()">
        <h3>Open account</h3>
        <label>Customer id <input name="ownerId" type="number" [(ngModel)]="ownerId" /></label>
        <button type="submit">Open account</button>
      </form>

      <form (ngSubmit)="handleFind()">
        <h3>Find account</h3>
        <label>Account id <input name="accountId" type="number" [(ngModel)]="accountId" /></label>
        <button type="submit">Get details</button>
        <button type="button" class="secondary" (click)="handleBalance()">Get balance</button>
      </form>
    </section>
  `,
})
export class AccountsPanelComponent {
  private readonly api = inject(AccountApiService);
  private readonly log = inject(ActivityLogService);

  ownerId: number | null = null;
  accountId: number | null = null;

  handleCreate(): void {
    this.log.run(`Open account for customer ${this.ownerId}`, this.api.createAccount(this.ownerId));
  }

  handleFind(): void {
    this.log.run(`Get account ${this.accountId}`, this.api.getAccount(this.accountId));
  }

  handleBalance(): void {
    this.log.run(`Balance of account ${this.accountId}`, this.api.getBalance(this.accountId));
  }
}
