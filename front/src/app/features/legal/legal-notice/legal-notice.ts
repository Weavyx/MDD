import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Public legal notice page. */
@Component({
  selector: 'app-legal-notice',
  imports: [RouterLink],
  templateUrl: './legal-notice.html',
  styleUrl: '../legal.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegalNotice {}
