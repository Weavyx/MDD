import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Public privacy policy page. */
@Component({
  selector: 'app-privacy-policy',
  templateUrl: './privacy-policy.html',
  styleUrl: '../legal.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivacyPolicy {}
