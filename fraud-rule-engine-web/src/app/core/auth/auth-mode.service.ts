import { HttpBackend, HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthModeService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly mode = signal<'local' | 'disabled'>('disabled');
  readonly error = signal(false);
  readonly local = computed(() => this.mode() === 'local');

  async load(): Promise<void> {
    try {
      const config = await firstValueFrom(
        this.http.get<unknown>('/app-config.json', {
          headers: { 'Cache-Control': 'no-cache' },
        }).pipe(timeout(10000)),
      );
      if (
        typeof config !== 'object' ||
        config === null ||
        !('authMode' in config) ||
        (config.authMode !== 'local' && config.authMode !== 'disabled')
      ) {
        throw new Error('Invalid authentication mode configuration');
      }
      this.mode.set(config.authMode);
      this.error.set(false);
    } catch {
      this.mode.set('disabled');
      this.error.set(true);
    }
  }
}
