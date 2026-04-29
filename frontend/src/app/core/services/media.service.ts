import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UploadResponse {
  url: string;
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly api = 'http://localhost:8080/api/media';

  constructor(private http: HttpClient) {}

  upload(file: File): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<UploadResponse>(`${this.api}/upload`, formData);
  }
}
