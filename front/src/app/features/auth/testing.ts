/** Test helper: types `value` into the input bound to `formControlName="name"`. */
export function typeInto(element: HTMLElement, name: string, value: string): void {
  const input = element.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`);
  if (!input) {
    throw new Error(`No input for ${name}`);
  }
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

/** Test helper: the trimmed texts of the `mat-error` currently displayed, in page order. */
export function errorTexts(element: HTMLElement): string[] {
  return Array.from(element.querySelectorAll('mat-error'), (e) =>
    (e.textContent ?? '').replace(/\s+/g, ' ').trim(),
  );
}
