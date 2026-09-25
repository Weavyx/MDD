import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import { TopicResponse } from '../topic.models';

/** A topic's name and description; the action button is projected by the page. */
@Component({
  selector: 'app-topic-card',
  imports: [MatCardModule],
  templateUrl: './topic-card.html',
  styleUrl: './topic-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopicCard {
  readonly topic = input.required<TopicResponse>();
}
