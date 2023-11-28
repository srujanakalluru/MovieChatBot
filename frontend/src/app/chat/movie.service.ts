import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface QueryResult {
  data?: Record<string, unknown>[];
  message?: string;
  error?: string;
}

export interface SyncStatus {
  lastSyncDate?: string;
  syncedAt?: string;
  syncInProgress?: boolean;
}

@Injectable({ providedIn: 'root' })
export class MovieService {
  private readonly http = inject(HttpClient);

  query(userInput: string): Observable<QueryResult> {
    const headers = new HttpHeaders({ 'Content-Type': 'text/plain' });
    return this.http.post<QueryResult>('/query', userInput, { headers });
  }

  transliterate(text: string, lang = 'te'): Observable<{ suggestions: string[] }> {
    return this.http.get<{ suggestions: string[] }>('/transliterate', { params: { text, lang } });
  }

  getSyncStatus(): Observable<SyncStatus> {
    return this.http.get<SyncStatus>('/internal/sync-status');
  }

  sync(): Observable<{ message?: string; error?: string }> {
    return this.http.post<{ message?: string; error?: string }>('/internal/sync', null);
  }

  syncRange(startDate: string, endDate: string): Observable<void> {
    return this.http.post<void>('/internal/sync/range', null, {
      params: { startDateStr: startDate, endDateStr: endDate }
    });
  }
}
