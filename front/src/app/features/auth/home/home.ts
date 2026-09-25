import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the auth feature. */
@Component({
  selector: 'app-home',
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {}
