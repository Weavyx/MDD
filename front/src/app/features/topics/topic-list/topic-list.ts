import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional page, to be implemented by the topics feature. */
@Component({
  selector: 'app-topic-list',
  templateUrl: './topic-list.html',
  styleUrl: './topic-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopicList {}
