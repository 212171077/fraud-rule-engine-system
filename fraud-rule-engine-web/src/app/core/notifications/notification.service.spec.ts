import { TestBed } from '@angular/core/testing';
import { NOTICE_DURATION_MS, NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('removes a notice automatically after ten seconds', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Saved', 'success');

    jasmine.clock().tick(NOTICE_DURATION_MS - 1);
    expect(service.notices().length).toBe(1);

    jasmine.clock().tick(1);
    expect(service.notices().length).toBe(0);
  });
});
