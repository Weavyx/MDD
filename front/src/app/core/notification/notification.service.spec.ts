import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  it('shows the message in a snack bar', () => {
    const open = vi.fn();
    TestBed.configureTestingModule({ providers: [{ provide: MatSnackBar, useValue: { open } }] });

    TestBed.inject(NotificationService).show('Ce topic n’existe pas');

    expect(open).toHaveBeenCalledWith('Ce topic n’existe pas', 'Fermer', { duration: 5000 });
  });
});
