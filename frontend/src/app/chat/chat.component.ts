import {
  Component,
  inject,
  signal,
  ViewChild,
  ElementRef,
  OnInit,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MovieService, QueryResult, SyncStatus } from './movie.service';
import { AuthService } from '../auth/auth.service';
import { finalize, firstValueFrom, timeout } from 'rxjs';

@Component({
  selector: 'app-chat',
  imports: [CommonModule, FormsModule, DatePipe, TranslatePipe],
  templateUrl: './chat.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './chat.component.scss'
})
export class ChatComponent implements OnInit {
  private readonly movieService = inject(MovieService);
  private readonly translate = inject(TranslateService);
  protected readonly auth = inject(AuthService);

  @ViewChild('inputRef') private inputRef!: ElementRef<HTMLTextAreaElement>;

  currentResult = signal<QueryResult | null>(null);
  inputText = signal('');
  loading = signal(false);
  isDark = signal(true);

  currentLang = signal('en');
  readonly languages = [
    { code: 'en', label: 'EN' },
    { code: 'fr', label: 'FR' },
    { code: 'te', label: 'తె' }
  ];

  translitEnabled = signal(true);

  showBackfill = signal(false);
  syncStatus = signal<SyncStatus | null>(null);
  backfillStatus = signal<string | null>(null);
  backfillDone = signal(false);
  backfillRunning = signal(false);

  showAdvanced = signal(false);
  maxSyncDate = (() => {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    return d.toLocaleDateString('en-CA');
  })();
  manualStart = signal('');
  manualEnd = signal('');
  manualStatus = signal<string | null>(null);
  manualDone = signal(false);
  manualRunning = signal(false);

  suggestions = signal<string[]>([]);

  ngOnInit(): void {
    const savedTheme = localStorage.getItem('theme');
    const dark = savedTheme ? savedTheme === 'dark' : true;
    this.isDark.set(dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');

    this.translate.setFallbackLang('en');
    const savedLang = localStorage.getItem('lang') ?? 'en';
    this.currentLang.set(savedLang);
    this.translate.use(savedLang);

    this.loadSuggestions();
    this.translate.onLangChange.subscribe(() => this.loadSuggestions());
  }

  private loadSuggestions(): void {
    this.translate.get('suggestions').subscribe((value) =>
      this.suggestions.set(Array.isArray(value) ? value : []));
  }

  setLang(code: string): void {
    this.currentLang.set(code);
    this.translate.use(code);
    localStorage.setItem('lang', code);
  }

  // Transliterate the romanized word that ends at `wordEnd` (exclusive), replacing only
  // those characters in place. Caret-aware and race-safe: it reads the live textarea, and
  // before writing it re-checks the word still occupies the same span, so anything typed
  // (or edited elsewhere) during the network round-trip is never clobbered.
  private async transliterateWord(wordEnd: number): Promise<void> {
    if (this.currentLang() !== 'te' || !this.translitEnabled()) return;
    const el = this.inputRef?.nativeElement;
    if (!el) return;
    const match = el.value.slice(0, wordEnd).match(/([a-zA-Z]+)$/);
    if (!match) return;
    const word = match[1];
    const start = wordEnd - word.length;
    let suggestion: string | undefined;
    try {
      const res = await firstValueFrom(this.movieService.transliterate(word).pipe(timeout(1500)));
      suggestion = res?.suggestions?.[0];
    } catch {
      return;
    }
    if (!suggestion || suggestion === word) return;
    // The romanized word must still sit exactly where we found it; otherwise the user
    // edited that region while we waited - leave their text alone.
    if (el.value.slice(start, wordEnd) !== word) return;
    const caret = el.selectionStart ?? wordEnd;
    const delta = suggestion.length - word.length;
    const newValue = el.value.slice(0, start) + suggestion + el.value.slice(wordEnd);
    this.setInputAndCaret(newValue, caret >= wordEnd ? caret + delta : caret);
  }

  // Update the bound signal, then restore the caret after Angular writes the value back
  // into the textarea (ngModel's writeback would otherwise drop it at the end).
  private setInputAndCaret(value: string, caret: number): void {
    this.inputText.set(value);
    setTimeout(() => {
      const el = this.inputRef?.nativeElement;
      if (el) {
        const pos = Math.min(caret, value.length);
        el.selectionStart = el.selectionEnd = pos;
      }
    });
  }

  logout(): void {
    this.auth.logout();
  }

  toggleTheme(): void {
    const dark = !this.isDark();
    this.isDark.set(dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    localStorage.setItem('theme', dark ? 'dark' : 'light');
  }

  getColumns(result: QueryResult): string[] {
    if (!result?.data?.length) return [];
    return Object.keys(result.data[0]);
  }

  formatColumnName(col: string): string {
    return col.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
  }

  getCellValue(row: Record<string, unknown>, col: string): string {
    const v = row[col];
    if (v == null) return '';
    if (typeof v === 'number') return v.toLocaleString();
    return String(v);
  }

  isSingleValue(result: QueryResult): boolean {
    return result?.data?.length === 1 && Object.keys(result.data[0]).length === 1;
  }

  getSingleValue(result: QueryResult): string {
    const row = result.data![0];
    return String(Object.values(row)[0] ?? '');
  }

  isSingleRow(result: QueryResult): boolean {
    return result?.data?.length === 1 && Object.keys(result.data[0]).length > 1;
  }

  isCardView(result: QueryResult): boolean {
    const count = result?.data?.length ?? 0;
    return count >= 2 && count <= 9;
  }

  isOverviewColumn(col: string): boolean {
    return col.toLowerCase() === 'overview';
  }

  titleColumn(result: QueryResult): string {
    return this.getColumns(result)[0];
  }

  metaColumns(result: QueryResult): string[] {
    return this.getColumns(result).slice(1);
  }

  send(text?: string): void {
    const query = (text ?? this.inputText()).trim();
    if (text) this.inputText.set(text);
    if (!query || this.loading()) return;

    this.loading.set(true);
    this.currentResult.set(null);

    this.movieService.query(query).pipe(
      finalize(() => this.loading.set(false))
    ).subscribe({
      next: (result) => this.currentResult.set(result),
      error: (e: { status?: number; error?: { error?: string; message?: string; errorReason?: string } }) => {
        const apiMsg = e?.error?.error ?? e?.error?.message ?? e?.error?.errorReason;
        const fallback = e?.status
          ? this.translate.instant('error.server', { status: e.status })
          : this.translate.instant('error.noServer');
        this.currentResult.set({ error: apiMsg ?? fallback });
      }
    });
  }

  private pollErrors = 0;

  runBackfill(): void {
    this.backfillRunning.set(true);
    this.backfillDone.set(false);
    this.backfillStatus.set(this.translate.instant('sync.syncing'));

    this.movieService.sync().subscribe({
      next: () => {
        this.pollErrors = 0;
        this.pollSyncStatus();
      },
      error: (e) => {
        if (e?.status === 409) {
          this.pollErrors = 0;
          this.pollSyncStatus();
          return;
        }
        this.backfillStatus.set(
          this.translate.instant('sync.failed', {
            error: e.error?.errorReason ?? e.error?.error ?? e.error?.message ?? e.message ?? this.translate.instant('common.unknownError')
          })
        );
        this.backfillRunning.set(false);
      }
    });
  }

  private pollSyncStatus(): void {
    this.movieService.getSyncStatus().subscribe({
      next: (s) => {
        this.pollErrors = 0;
        this.syncStatus.set(s);
        if (s.syncInProgress) {
          setTimeout(() => this.pollSyncStatus(), 3000);
        } else {
          this.backfillStatus.set(
            this.translate.instant('sync.doneThrough', {
              date: s.lastSyncDate ?? this.translate.instant('common.unknown')
            })
          );
          this.backfillDone.set(true);
          this.backfillRunning.set(false);
        }
      },
      error: () => {
        if (++this.pollErrors > 5) {
          this.backfillStatus.set(
            this.translate.instant('sync.failed', {
              error: this.translate.instant('common.unknownError')
            })
          );
          this.backfillRunning.set(false);
          return;
        }
        setTimeout(() => this.pollSyncStatus(), 5000);
      }
    });
  }

  openBackfill(): void {
    this.backfillStatus.set(null);
    this.backfillDone.set(false);
    this.showBackfill.set(true);
    this.movieService.getSyncStatus().subscribe({
      next: (s) => {
        this.syncStatus.set(s);
        if (s.syncInProgress && !this.backfillRunning()) {
          this.backfillRunning.set(true);
          this.backfillStatus.set(this.translate.instant('sync.syncing'));
          this.pollSyncStatus();
        }
      },
      error: () => this.syncStatus.set(null)
    });
  }

  closeBackfill(): void {
    if (!this.manualRunning()) {
      this.showBackfill.set(false);
      this.showAdvanced.set(false);
    }
  }

  runManualSync(): void {
    const start = this.manualStart();
    const end   = this.manualEnd();
    if (!start || !end) {
      this.manualStatus.set(this.translate.instant('sync.setBothDates'));
      return;
    }
    if (start > this.maxSyncDate || end > this.maxSyncDate) {
      this.manualStatus.set(this.translate.instant('sync.dateTooLate'));
      return;
    }

    const fmt = (d: string) => d.split('-').reverse().join('-');

    this.manualRunning.set(true);
    this.manualDone.set(false);
    this.manualStatus.set(this.translate.instant('sync.syncing'));

    this.movieService.syncRange(fmt(start), fmt(end)).subscribe({
      next: () => {
        this.manualStatus.set(this.translate.instant('sync.rangeDone'));
        this.manualDone.set(true);
        this.manualRunning.set(false);
      },
      error: (e) => {
        this.manualStatus.set(
          this.translate.instant('sync.rangeFailed', {
            error: e.error?.errorReason ?? e.error?.message ?? e.message ?? this.translate.instant('common.unknownError')
          })
        );
        this.manualRunning.set(false);
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      const el = this.inputRef?.nativeElement;
      const caret = el?.selectionStart ?? this.inputText().length;
      // Transliterate the word at the caret, then submit regardless of the outcome.
      this.transliterateWord(caret).finally(() => this.send());
    }
  }

  // A space is typed natively (correct caret/selection handling); we react afterwards and
  // transliterate the word that now precedes it. No preventDefault, so editing anywhere in
  // the text - including replacing a selection - behaves normally.
  onInput(event: Event): void {
    const e = event as InputEvent;
    if (e.isComposing || e.data !== ' ') return;
    if (this.currentLang() !== 'te' || !this.translitEnabled()) return;
    const caret = this.inputRef?.nativeElement?.selectionStart ?? 0;
    // caret sits just after the typed space, so the word ends one position before it.
    this.transliterateWord(caret - 1);
  }
}
