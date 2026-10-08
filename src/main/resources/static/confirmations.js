/*
 *
 * TinyTopics
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
// Shared styled confirmation for forms marked with data-confirm.
(() => {
    const dialog = document.createElement('dialog');
    dialog.className = 'confirmation-dialog';
    dialog.setAttribute('aria-labelledby', 'confirmation-title');
    dialog.setAttribute('aria-describedby', 'confirmation-message');
    dialog.innerHTML = `
        <form method="dialog">
            <h2 id="confirmation-title">Confirm deletion</h2>
            <p id="confirmation-message"></p>
            <div class="dialog-actions">
                <button class="secondary" value="cancel" autofocus>Cancel</button>
                <button class="danger" value="confirm">Delete topic</button>
            </div>
        </form>`;
    document.body.append(dialog);
    let pendingForm = null;
    let trigger = null;
    const confirmed = new WeakSet();

    document.addEventListener('submit', event => {
        const form = event.target;
        if (!form.matches('form[data-confirm]')) return;
        if (confirmed.delete(form)) return;
        event.preventDefault();
        if (dialog.open) return;
        pendingForm = form;
        trigger = event.submitter || document.activeElement;
        const topic = form.dataset.confirmTopic;
        dialog.querySelector('#confirmation-message').textContent = topic
            ? `Delete topic “${topic}”? This permanently removes its definition and saved values.`
            : form.dataset.confirm;
        dialog.returnValue = 'cancel';
        dialog.showModal();
    });

    dialog.addEventListener('close', () => {
        const form = pendingForm;
        const button = trigger;
        pendingForm = null;
        trigger = null;
        button?.focus();
        if (dialog.returnValue === 'confirm' && form) {
            confirmed.add(form);
            form.requestSubmit();
        }
    });
    // Escape uses the dialog's native cancel behavior and never submits deletion.
})();
