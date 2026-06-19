import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UploadResponse {
  url: string;
  mediaType?: string;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly api = 'http://localhost:8080/api/media';

  constructor(private http: HttpClient) {}

  upload(file: File): Observable<UploadResponse> {
    return new Observable(observer => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        const base64Data = (reader.result as string).split(',')[1];
        this.http.post<UploadResponse>(`${this.api}/upload`, {
          data: base64Data,
          fileName: file.name,
          mediaType: file.type.startsWith('image') ? 'image' : 'video'
        }).subscribe({
          next: (res) => observer.next(res),
          error: (err) => observer.error(err),
          complete: () => observer.complete()
        });
      };
      reader.onerror = (error) => observer.error(error);
    });
  }
}
