import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the auth feature. */
@Component({
  selector: 'app-register',
  templateUrl: './register.html',
  styleUrl: './register.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {}
