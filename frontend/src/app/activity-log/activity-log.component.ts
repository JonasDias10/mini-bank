import { DatePipe, JsonPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivityLogService } from '../services/activity-log.service';

@Component({
  selector: 'app-activity-log',
  imports: [DatePipe, JsonPipe],
  styleUrl: './activity-log.component.css',
  template: `
    <section class="card">
      <header>
        <h2>Responses</h2>
        <button type="button" class="secondary" (click)="log.clear()">Clear</button>
      </header>

      @for (entry of log.entries(); track entry.id) {
        <article [class]="entry.tone">
          <div class="entry-title">
            <strong>{{ entry.label }}</strong>
            <span>{{ entry.tag }} · {{ entry.time | date: 'HH:mm:ss' }}</span>
          </div>
          @if (entry.error; as error) {
            <p>{{ error.message }}</p>
            @if (error.fieldErrors; as fieldErrors) {
              <pre>{{ fieldErrors | json }}</pre>
            }
          } @else {
            <pre>{{ entry.data | json }}</pre>
          }
        </article>
      } @empty {
        <p class="empty">Responses from the API will appear here.</p>
      }
    </section>
  `,
})
export class ActivityLogComponent {
  protected readonly log = inject(ActivityLogService);
}
