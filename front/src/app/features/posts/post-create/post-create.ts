import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the posts feature. */
@Component({
  selector: 'app-post-create',
  templateUrl: './post-create.html',
  styleUrl: './post-create.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PostCreate {}
