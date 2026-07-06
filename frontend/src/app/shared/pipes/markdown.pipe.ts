import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'markdown',
  standalone: true
})
export class MarkdownPipe implements PipeTransform {

  constructor(private sanitizer: DomSanitizer) {}

  transform(value: string | undefined): SafeHtml {
    if (!value) return '';

    const codeBlocks: string[] = [];
    const inlineCodes: string[] = [];

    // 1. Escape raw HTML FIRST. Everything downstream operates on
    //    already-safe text, so user-typed <script>, onerror=, etc.
    //    can never reach the DOM as real tags/attributes.
    let html = this.escapeHtml(value);

    // 2. Extract multiline code blocks: ```lang\ncode``` or ```code```
    html = html.replace(/```([\s\S]*?)```/g, (match, code) => {
      let cleanedCode = code;
      
      // Strip language Specifier if present (e.g. ```javascript\n)
      const firstLineBreak = code.indexOf('\n');
      if (firstLineBreak !== -1) {
        const firstLine = code.substring(0, firstLineBreak).trim();
        if (/^[a-zA-Z0-9+#-]+$/.test(firstLine)) {
          cleanedCode = code.substring(firstLineBreak + 1);
        }
      }
      
      // Trim leading/trailing newlines
      cleanedCode = cleanedCode.replace(/^\n+|\n+$/g, '');

      const placeholder = `<!--CODEBLOCK_${codeBlocks.length}-->`;
      codeBlocks.push(`<pre class="md-code-block"><code>${cleanedCode}</code></pre>`);
      return placeholder;
    });

    // 3. Extract inline code: `code`
    html = html.replace(/`([^`\n]+)`/g, (match, code) => {
      const placeholder = `<!--INLINECODE_${inlineCodes.length}-->`;
      inlineCodes.push(`<code class="md-inline-code">${code}</code>`);
      return placeholder;
    });

    // 4. Headers, bold, italic, links, newlines
    html = html
      .replace(/^### (.*$)/gm, '<h3>$1</h3>')
      .replace(/^## (.*$)/gm, '<h2>$1</h2>')
      .replace(/^# (.*$)/gm, '<h1>$1</h1>')
      .replace(/\*\*(.*?)\*\*/g, '<b>$1</b>')
      .replace(/\*(.*?)\*/g, '<i>$1</i>')
      .replace(/\[(.*?)\]\((.*?)\)/g, (_match, text, url) => {
        const safeUrl = this.sanitizeUrl(url);
        return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="md-link">${text}</a>`;
      })
      .replace(/\n/g, '<br>');

    // 5. Restore multiline code blocks
    html = html.replace(/<!--CODEBLOCK_(\d+)-->/g, (match, index) => {
      return codeBlocks[parseInt(index, 10)];
    });

    // 6. Restore inline code
    html = html.replace(/<!--INLINECODE_(\d+)-->/g, (match, index) => {
      return inlineCodes[parseInt(index, 10)];
    });

    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  private escapeHtml(str: string): string {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  private sanitizeUrl(url: string): string {
    const trimmed = url.trim();
    // Whitelist safe protocols/relative paths instead of blacklisting
    // dangerous ones — blacklists miss encoded/whitespace variants
    // of javascript:, data:, vbscript:, etc.
    if (/^(https?:|mailto:|\/|#)/i.test(trimmed)) {
      return trimmed;
    }
    return '#';
  }
}