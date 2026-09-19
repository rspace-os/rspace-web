export interface MailpitAddress {
  Name: string;
  Address: string;
}

export interface MailpitMessageSummary {
  ID: string;
  MessageID: string;
  From: MailpitAddress;
  To: MailpitAddress[];
  Cc: MailpitAddress[];
  Bcc: MailpitAddress[];
  Subject: string;
  Created: string;
  Size: number;
}

export interface MailpitMessagesResponse {
  total: number;
  unread: number;
  count: number;
  messages_count: number;
  start: number;
  messages: MailpitMessageSummary[];
}

export interface MailpitMessage extends MailpitMessageSummary {
  Text: string;
  HTML: string;
  Headers: Record<string, string[]>;
}
