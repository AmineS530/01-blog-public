import { Injectable } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
  duration?: number;
}

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel?: () => void;
}

@Injectable({
  providedIn: 'root'
})
export class FeedbackService {
  toasts: Toast[] = [];
  private toastIdCounter = 0;

  confirmState: {
    isOpen: boolean;
    title: string;
    message: string;
    confirmText: string;
    cancelText: string;
    onConfirm?: () => void;
    onCancel?: () => void;
  } = {
    isOpen: false,
    title: '',
    message: '',
    confirmText: 'Confirm',
    cancelText: 'Cancel'
  };

  showToast(message: string, type: 'success' | 'error' | 'info' = 'info', duration = 4000): void {
    const id = this.toastIdCounter++;
    const toast: Toast = { id, message, type, duration };
    this.toasts.push(toast);

    setTimeout(() => {
      this.removeToast(id);
    }, duration);
  }

  removeToast(id: number): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
  }

  askConfirmation(options: ConfirmOptions): void {
    this.confirmState = {
      isOpen: true,
      title: options.title,
      message: options.message,
      confirmText: options.confirmText || 'Confirm',
      cancelText: options.cancelText || 'Cancel',
      onConfirm: () => {
        this.confirmState.isOpen = false;
        if (options.onConfirm) options.onConfirm();
      },
      onCancel: () => {
        this.confirmState.isOpen = false;
        if (options.onCancel) options.onCancel();
      }
    };
  }

  closeConfirmation(): void {
    if (this.confirmState.onCancel) {
      this.confirmState.onCancel();
    } else {
      this.confirmState.isOpen = false;
    }
  }
}
