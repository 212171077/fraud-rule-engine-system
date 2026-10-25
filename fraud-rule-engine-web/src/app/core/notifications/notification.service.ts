import { computed, Injectable, signal } from '@angular/core';

export type NoticeTone = 'error' | 'success' | 'info';

export interface Notice {
  id: number;
  tone: NoticeTone;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly nextId = signal(0);
  private readonly noticesState = signal<Notice[]>([]);
  readonly notices = computed(() => this.noticesState());

  show(message: string, tone: NoticeTone = 'info'): void {
    const id = this.nextId() + 1;
    this.nextId.set(id);
    this.noticesState.update((notices) => [...notices, { id, tone, message }]);
  }

  dismiss(id: number): void {
    this.noticesState.update((notices) => notices.filter((notice) => notice.id !== id));
  }
}
