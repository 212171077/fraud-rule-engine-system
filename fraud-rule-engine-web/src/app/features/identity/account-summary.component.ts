import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { UserProfile } from '../../core/api/api.models';
import { humanizeValue } from '../../shared/labels';

@Component({
  selector: 'app-account-summary',
  imports: [DatePipe],
  template: `
    @if (user(); as account) {
      <header class="identity">
        <span class="avatar" aria-hidden="true">{{ initials() }}</span>
        <div class="who">
          <h2>{{ account.name }} {{ account.surname }}</h2>
          <p class="email">{{ account.email }}</p>
          <div class="badges">
            <span class="chip role">{{ roleLabel() }}</span>
            <span class="chip" [class]="'chip status-' + account.status.toLowerCase()">{{ account.status }}</span>
          </div>
        </div>
      </header>
      <dl class="facts">
        <div><dt>Role</dt><dd>{{ account.role }}</dd></div>
        <div><dt>Status</dt><dd>{{ account.status }}</dd></div>
        <div><dt>Created (UTC)</dt><dd>{{ account.createdAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd></div>
        @if (showUpdated()) {
          <div><dt>Updated (UTC)</dt><dd>{{ account.updatedAt | date: 'yyyy-MM-dd HH:mm:ss' : 'UTC' }}</dd></div>
        }
      </dl>
    }
  `,
  styleUrl: './account-summary.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSummaryComponent {
  readonly user = input.required<UserProfile>();
  readonly showUpdated = input(false);
  readonly initials = computed(() => {
    const { name, surname } = this.user();
    return `${name.charAt(0)}${surname.charAt(0)}`.toUpperCase();
  });
  readonly roleLabel = computed(() => humanizeValue(this.user().role));
}
