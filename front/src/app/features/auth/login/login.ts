import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the auth feature. */
@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {}
