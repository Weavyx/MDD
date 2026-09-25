import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the posts feature. */
@Component({
  selector: 'app-post-detail',
  templateUrl: './post-detail.html',
  styleUrl: './post-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostDetail {}
