import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  it('renders the application shell', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('app-shell mat-toolbar')?.textContent).toContain('MDD');
    expect(element.querySelector('app-shell router-outlet')).not.toBeNull();
  });
});
