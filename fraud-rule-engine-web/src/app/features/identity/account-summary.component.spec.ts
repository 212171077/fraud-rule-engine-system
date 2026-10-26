import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserProfile } from '../../core/api/api.models';
import { AccountSummaryComponent } from './account-summary.component';

describe('AccountSummaryComponent', () => {
  const user: UserProfile = {
    id: 'u1', name: 'Thandi', surname: 'Nkosi', email: 'thandi@example.test',
    role: 'FRAUD_ANALYST', status: 'ACTIVE',
    createdAt: '2026-10-01T08:00:00Z', updatedAt: '2026-10-02T09:30:00Z',
  };
  let fixture: ComponentFixture<AccountSummaryComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountSummaryComponent);
    fixture.componentRef.setInput('user', user);
    fixture.detectChanges();
  });

  it('shows initials, readable role and status chip', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.avatar')?.textContent).toContain('TN');
    expect(element.querySelector('.chip.role')?.textContent).toContain('Fraud Analyst');
    expect(element.querySelector('.status-active')?.textContent).toContain('ACTIVE');
    expect(element.textContent).not.toContain('Updated (UTC)');
  });

  it('shows the updated time only when requested', () => {
    fixture.componentRef.setInput('showUpdated', true);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026-10-02 09:30:00');
  });
});
