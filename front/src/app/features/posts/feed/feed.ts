import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the posts feature. */
@Component({
  selector: 'app-feed',
  templateUrl: './feed.html',
  styleUrl: './feed.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Feed {}
