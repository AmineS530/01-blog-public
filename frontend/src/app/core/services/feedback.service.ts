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

export interface PromptOptions {
  title: string;
  message: string;
  placeholder?: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: (value: string) => void;
  onCancel?: () => void;
}

@Injectable({
  providedIn: 'root',
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
    cancelText: 'Cancel',
  };

  promptState: {
    isOpen: boolean;
    title: string;
    message: string;
    value: string;
    placeholder: string;
    confirmText: string;
    cancelText: string;
    onConfirm?: (value: string) => void;
    onCancel?: () => void;
  } = {
    isOpen: false,
    title: '',
    message: '',
    value: '',
    placeholder: '',
    confirmText: 'Submit',
    cancelText: 'Cancel',
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
    this.toasts = this.toasts.filter((t) => t.id !== id);
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
      },
    };
  }

  closeConfirmation(): void {
    if (this.confirmState.onCancel) {
      this.confirmState.onCancel();
    } else {
      this.confirmState.isOpen = false;
    }
  }

  askPrompt(options: PromptOptions): void {
    this.promptState = {
      isOpen: true,
      title: options.title,
      message: options.message,
      value: '',
      placeholder: options.placeholder || 'Enter details...',
      confirmText: options.confirmText || 'Submit',
      cancelText: options.cancelText || 'Cancel',
      onConfirm: (val) => {
        this.promptState.isOpen = false;
        if (options.onConfirm) options.onConfirm(val);
      },
      onCancel: () => {
        this.promptState.isOpen = false;
        if (options.onCancel) options.onCancel();
      },
    };
  }

  confirmPrompt(): void {
    if (this.promptState.onConfirm) {
      this.promptState.onConfirm(this.promptState.value);
    }
  }

  closePrompt(): void {
    if (this.promptState.onCancel) {
      this.promptState.onCancel();
    } else {
      this.promptState.isOpen = false;
    }
  }
}
