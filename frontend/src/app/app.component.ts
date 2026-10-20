import { Component } from '@angular/core';
import { ActivityLogComponent } from './activity-log/activity-log.component';
import { AccountsPanelComponent } from './panels/accounts-panel.component';
import { CustomersPanelComponent } from './panels/customers-panel.component';
import { TransactionsPanelComponent } from './panels/transactions-panel.component';

@Component({
  selector: 'app-root',
  imports: [CustomersPanelComponent, AccountsPanelComponent, TransactionsPanelComponent, ActivityLogComponent],
  styles: `
    .page {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 16px;
    }

    .page > h1 {
      font-size: 22px;
    }

    .page > p {
      color: var(--muted);
      margin: 4px 0 20px;
    }

    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 16px;
      align-items: start;
    }

    .panels {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    app-activity-log {
      position: sticky;
      top: 16px;
      max-height: calc(100vh - 32px);
      overflow-y: auto;
    }

    @media (max-width: 800px) {
      .layout {
        grid-template-columns: 1fr;
      }

      app-activity-log {
        position: static;
        max-height: none;
      }
    }
  `,
  template: `
    <main class="page">
      <h1>SimpleBank</h1>
      <p>Test console for account-service and transaction-service.</p>

      <div class="layout">
        <div class="panels">
          <app-customers-panel />
          <app-accounts-panel />
          <app-transactions-panel />
        </div>
        <app-activity-log />
      </div>
    </main>
  `,
})
export class AppComponent {}
