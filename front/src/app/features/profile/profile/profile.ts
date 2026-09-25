import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the profile feature. */
@Component({
  selector: 'app-profile',
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Profile {}
