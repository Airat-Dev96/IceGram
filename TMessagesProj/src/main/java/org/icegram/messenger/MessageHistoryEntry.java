/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

public final class MessageHistoryEntry {
    public static final int TYPE_EDIT = 1;
    public static final int TYPE_DELETE = 2;

    public final long id;
    public final long dialogId;
    public final int messageId;
    public final int messageDate;
    public final int changedAt;
    public final int type;
    public final String text;

    public MessageHistoryEntry(long id, long dialogId, int messageId, int messageDate, int changedAt, int type, String text) {
        this.id = id;
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.messageDate = messageDate;
        this.changedAt = changedAt;
        this.type = type;
        this.text = text;
    }
}
