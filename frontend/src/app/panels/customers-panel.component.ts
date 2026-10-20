import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AccountApiService } from '../services/account-api.service';
import { ActivityLogService } from '../services/activity-log.service';

@Component({
  selector: 'app-customers-panel',
  imports: [FormsModule],
  template: `
    <section class="card">
      <h2>Customers</h2>

      <form (ngSubmit)="handleCreate()">
        <h3>Register customer</h3>
        <label>Full name <input name="fullName" [(ngModel)]="fullName" placeholder="Ana Souza" /></label>
        <label>Email <input name="email" [(ngModel)]="email" placeholder="ana@example.com" /></label>
        <button type="submit">Register</button>
      </form>

      <form (ngSubmit)="handleFind()">
        <h3>Find customer</h3>
        <label>Customer id <input name="customerId" type="number" [(ngModel)]="customerId" /></label>
        <button type="submit">Get customer</button>
        <button type="button" class="secondary" (click)="handleListAccounts()">List accounts</button>
      </form>
    </section>
  `,
})
export class CustomersPanelComponent {
  private readonly api = inject(AccountApiService);
  private readonly log = inject(ActivityLogService);

  fullName = '';
  email = '';
  customerId: number | null = null;

  handleCreate(): void {
    this.log.run('Register customer', this.api.createCustomer({ fullName: this.fullName, email: this.email }));
  }

  handleFind(): void {
    this.log.run(`Get customer ${this.customerId}`, this.api.getCustomer(this.customerId));
  }

  handleListAccounts(): void {
    this.log.run(`Accounts of customer ${this.customerId}`, this.api.getCustomerAccounts(this.customerId));
  }
}
